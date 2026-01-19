package com.hft.engine;

import com.hft.core.metrics.OrderMetrics;
import com.hft.core.model.*;
import com.hft.core.port.OrderPort;
import com.hft.engine.event.TradingEvent;
import com.hft.engine.event.TradingEventFactory;
import com.hft.engine.handler.MetricsHandler;
import com.hft.engine.handler.OrderHandler;
import com.hft.engine.handler.PositionHandler;
import com.hft.engine.risk.RiskContextAdapter;
import com.hft.engine.service.OrderManager;
import com.hft.engine.service.PositionManager;
import com.hft.persistence.PersistenceManager;
import com.hft.risk.*;
import com.hft.risk.rules.StandardRules;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enhanced trading engine that integrates with the hft-risk and hft-persistence modules.
 * Provides comprehensive risk management and trade journaling.
 */
public class IntegratedTradingEngine implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(IntegratedTradingEngine.class);

    private static final int DEFAULT_RING_BUFFER_SIZE = 1024 * 64;

    private final Disruptor<TradingEvent> disruptor;
    private final RingBuffer<TradingEvent> ringBuffer;

    // Services
    private final OrderManager orderManager;
    private final PositionManager positionManager;
    private final RiskEngine riskEngine;
    private final RiskContextAdapter riskContext;
    private final PersistenceManager persistence;
    private final OrderMetrics orderMetrics;

    // Handlers
    private final OrderHandler orderHandler;
    private final PositionHandler positionHandler;
    private final MetricsHandler metricsHandler;

    private final AtomicBoolean running = new AtomicBoolean(false);

    // Event translators
    private static final EventTranslatorOneArg<TradingEvent, Order> NEW_ORDER_TRANSLATOR =
            (event, sequence, order) -> event.populateNewOrder(order);

    private static final EventTranslatorOneArg<TradingEvent, Order> ORDER_ACCEPTED_TRANSLATOR =
            (event, sequence, order) -> event.populateOrderAccepted(order);

    private static final EventTranslatorOneArg<TradingEvent, Quote> QUOTE_TRANSLATOR =
            (event, sequence, quote) -> event.populateQuoteUpdate(quote);

    private static final EventTranslatorOneArg<TradingEvent, Trade> TRADE_TRANSLATOR =
            (event, sequence, trade) -> event.populateTradeUpdate(trade);

    /**
     * Creates a new integrated trading engine with default settings.
     */
    public IntegratedTradingEngine() {
        this(RiskLimits.defaults(), PersistenceManager.inMemory());
    }

    /**
     * Creates a new integrated trading engine with specified risk limits.
     */
    public IntegratedTradingEngine(RiskLimits riskLimits) {
        this(riskLimits, PersistenceManager.inMemory());
    }

    /**
     * Creates a new integrated trading engine with specified risk limits and persistence.
     */
    public IntegratedTradingEngine(RiskLimits riskLimits, PersistenceManager persistence) {
        this(riskLimits, persistence, DEFAULT_RING_BUFFER_SIZE);
    }

    /**
     * Creates a new integrated trading engine with full configuration.
     */
    public IntegratedTradingEngine(RiskLimits riskLimits, PersistenceManager persistence, int ringBufferSize) {
        // Initialize services
        this.orderManager = new OrderManager();
        this.positionManager = new PositionManager();
        this.persistence = persistence;
        this.orderMetrics = new OrderMetrics();

        // Initialize risk management
        this.riskContext = new RiskContextAdapter(positionManager, riskLimits);
        this.riskEngine = new RiskEngine(riskLimits, riskContext);
        StandardRules.addAllTo(riskEngine);

        // Add risk event listener for persistence
        riskEngine.addListener(new RiskPersistenceListener());

        // Initialize handlers (no separate RiskHandler needed - risk is checked before publish)
        this.orderHandler = new OrderHandler(orderManager);
        this.positionHandler = new PositionHandler(positionManager);
        this.metricsHandler = new MetricsHandler(orderMetrics);

        // Create disruptor
        this.disruptor = new Disruptor<>(
                TradingEventFactory.INSTANCE,
                ringBufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                new BusySpinWaitStrategy()
        );

        // Configure handler chain - risk is checked before publishing, so no risk handler here
        disruptor.handleEventsWith(orderHandler)
                .then(positionHandler)
                .then(metricsHandler);

        this.ringBuffer = disruptor.getRingBuffer();

        log.info("IntegratedTradingEngine initialized with ring buffer size: {}", ringBufferSize);
    }

    /**
     * Starts the trading engine.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            disruptor.start();
            riskEngine.enable();
            persistence.logEngineStarted();
            log.info("IntegratedTradingEngine started");
        }
    }

    /**
     * Stops the trading engine.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            riskEngine.disable("Engine shutdown");
            disruptor.shutdown();
            persistence.flush();
            persistence.logEngineStopped();
            log.info("IntegratedTradingEngine stopped");
        }
    }

    /**
     * Registers an exchange adapter for order routing.
     */
    public void registerExchange(Exchange exchange, OrderPort orderPort) {
        orderHandler.registerOrderPort(exchange, orderPort);
    }

    /**
     * Submits a new order through the engine.
     *
     * @return rejection reason if rejected, null if accepted
     */
    public String submitOrder(Order order) {
        if (!running.get()) {
            return "Trading engine not running";
        }

        // Pre-trade risk check
        RiskCheckResult result = riskEngine.checkPreTrade(order);
        if (result.isRejected()) {
            orderManager.rejectOrder(order, result.reason());
            persistence.logOrderRejected(order, result.reason());
            persistence.logRiskCheckFailed(String.valueOf(order.getClientOrderId()), result.reason());
            return result.reason();
        }

        // Save order to repository
        persistence.saveOrder(order);
        persistence.logOrderSubmitted(order);

        // Update risk context counters
        riskContext.incrementOrdersSubmitted();

        // Publish to ring buffer
        ringBuffer.publishEvent(NEW_ORDER_TRANSLATOR, order);
        return null;
    }

    /**
     * Cancels an existing order.
     */
    public void cancelOrder(long clientOrderId, Symbol symbol) {
        if (!running.get()) {
            log.warn("Cannot cancel order - engine not running");
            return;
        }

        ringBuffer.publishEvent((event, sequence) ->
                event.populateCancelOrder(clientOrderId, symbol));
    }

    /**
     * Publishes an order acceptance event.
     */
    public void onOrderAccepted(Order order) {
        persistence.saveOrder(order);
        ringBuffer.publishEvent(ORDER_ACCEPTED_TRANSLATOR, order);
    }

    /**
     * Publishes an order fill event.
     */
    public void onOrderFilled(Order order, long fillQuantity, long fillPrice) {
        // Record fill with risk engine
        riskEngine.recordFill(order.getSymbol(), order.getSide(), fillQuantity, fillPrice);

        // Update risk context notional
        long notional = fillQuantity * fillPrice;
        riskContext.addNotionalTraded(notional);

        // Create trade record
        Trade trade = new Trade();
        trade.setSymbol(order.getSymbol());
        trade.setSide(order.getSide());
        trade.setQuantity(fillQuantity);
        trade.setPrice(fillPrice);
        trade.setClientOrderId(order.getClientOrderId());
        trade.setExecutedAt(System.nanoTime());

        // Persist trade
        persistence.recordTrade(trade);
        persistence.saveOrder(order);

        ringBuffer.publishEvent((event, sequence) ->
                event.populateOrderFilled(order, fillQuantity, fillPrice));
    }

    /**
     * Publishes an order rejection event.
     */
    public void onOrderRejected(Order order, String reason) {
        persistence.saveOrder(order);
        persistence.logOrderRejected(order, reason);

        ringBuffer.publishEvent((event, sequence) ->
                event.populateOrderRejected(order, reason));
    }

    /**
     * Publishes an order cancellation event.
     */
    public void onOrderCancelled(Order order) {
        persistence.saveOrder(order);

        ringBuffer.publishEvent((event, sequence) ->
                event.populateOrderCancelled(order));
    }

    /**
     * Publishes a quote update event.
     */
    public void onQuoteUpdate(Quote quote) {
        ringBuffer.publishEvent(QUOTE_TRANSLATOR, quote);
    }

    /**
     * Publishes a trade update event.
     */
    public void onTradeUpdate(Trade trade) {
        ringBuffer.publishEvent(TRADE_TRANSLATOR, trade);
    }

    // Getters

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public PositionManager getPositionManager() {
        return positionManager;
    }

    public RiskEngine getRiskEngine() {
        return riskEngine;
    }

    public PersistenceManager getPersistence() {
        return persistence;
    }

    public OrderMetrics getOrderMetrics() {
        return orderMetrics;
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getRemainingCapacity() {
        return ringBuffer.remainingCapacity();
    }

    public long getCursor() {
        return ringBuffer.getCursor();
    }

    /**
     * Resets daily counters.
     */
    public void resetDailyCounters() {
        riskEngine.resetDailyCounters();
        riskContext.resetDailyCounters();
        orderMetrics.reset();
        log.info("Daily counters reset");
    }

    /**
     * Takes a snapshot of all positions.
     */
    public void snapshotPositions() {
        Map<Symbol, Position> positions = new java.util.HashMap<>();
        for (Position pos : positionManager.getAllPositions()) {
            positions.put(pos.getSymbol(), pos);
        }
        persistence.snapshotPositions(positions);
    }

    /**
     * Gets a snapshot of current engine state.
     */
    public EngineSnapshot getSnapshot() {
        return new EngineSnapshot(
                running.get(),
                ringBuffer.getCursor(),
                ringBuffer.remainingCapacity(),
                orderManager.getOrderCount(),
                orderManager.getActiveOrders().size(),
                positionManager.getSnapshot(),
                orderMetrics.snapshot(),
                riskEngine.snapshot()
        );
    }

    @Override
    public void close() {
        stop();
        persistence.close();
    }

    /**
     * Immutable snapshot of engine state.
     */
    public record EngineSnapshot(
            boolean running,
            long eventsPublished,
            long ringBufferCapacity,
            int totalOrders,
            int activeOrders,
            PositionManager.PositionSnapshot positions,
            OrderMetrics.MetricsSnapshot metrics,
            RiskEngine.RiskSnapshot risk
    ) {}

    /**
     * Risk event listener that logs to persistence.
     */
    private class RiskPersistenceListener implements RiskEventListener {
        @Override
        public void onOrderRejected(Order order, RiskCheckResult result) {
            persistence.logRiskCheckFailed(String.valueOf(order.getClientOrderId()), result.reason());
        }

        @Override
        public void onCircuitBreakerTripped(String reason) {
            persistence.logCircuitBreakerTripped(reason);
        }

        @Override
        public void onRiskEngineDisabled(String reason) {
            persistence.getAuditLog().log(
                    com.hft.persistence.AuditLog.EventType.TRADING_DISABLED,
                    "Trading disabled: " + reason
            );
        }
    }
}
