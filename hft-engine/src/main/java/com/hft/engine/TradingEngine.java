package com.hft.engine;

import com.hft.core.metrics.OrderMetrics;
import com.hft.core.model.*;
import com.hft.core.port.OrderPort;
import com.hft.engine.event.TradingEvent;
import com.hft.engine.event.TradingEventFactory;
import com.hft.engine.handler.MetricsHandler;
import com.hft.engine.handler.OrderHandler;
import com.hft.engine.handler.PositionHandler;
import com.hft.engine.handler.RiskHandler;
import com.hft.engine.service.OrderManager;
import com.hft.engine.service.PositionManager;
import com.hft.engine.service.RiskManager;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main trading engine that coordinates all components using LMAX Disruptor.
 *
 * Architecture:
 * - Ring buffer receives all trading events
 * - Handlers process events in parallel/sequential chains
 * - RiskHandler -> OrderHandler -> PositionHandler -> MetricsHandler
 */
public class TradingEngine {
    private static final Logger log = LoggerFactory.getLogger(TradingEngine.class);

    private static final int DEFAULT_RING_BUFFER_SIZE = 1024 * 64; // 64K events

    private final Disruptor<TradingEvent> disruptor;
    private final RingBuffer<TradingEvent> ringBuffer;

    // Services
    private final OrderManager orderManager;
    private final PositionManager positionManager;
    private final RiskManager riskManager;
    private final OrderMetrics orderMetrics;

    // Handlers
    private final OrderHandler orderHandler;
    private final PositionHandler positionHandler;
    private final RiskHandler riskHandler;
    private final MetricsHandler metricsHandler;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile long startTimeMillis = 0;

    // Event translators for zero-allocation publishing
    private static final EventTranslatorOneArg<TradingEvent, Order> NEW_ORDER_TRANSLATOR =
            (event, sequence, order) -> event.populateNewOrder(order);

    private static final EventTranslatorOneArg<TradingEvent, Order> ORDER_ACCEPTED_TRANSLATOR =
            (event, sequence, order) -> event.populateOrderAccepted(order);

    private static final EventTranslatorOneArg<TradingEvent, Quote> QUOTE_TRANSLATOR =
            (event, sequence, quote) -> event.populateQuoteUpdate(quote);

    private static final EventTranslatorOneArg<TradingEvent, Trade> TRADE_TRANSLATOR =
            (event, sequence, trade) -> event.populateTradeUpdate(trade);

    public TradingEngine() {
        this(RiskManager.RiskLimits.defaults(), DEFAULT_RING_BUFFER_SIZE);
    }

    public TradingEngine(RiskManager.RiskLimits riskLimits) {
        this(riskLimits, DEFAULT_RING_BUFFER_SIZE);
    }

    public TradingEngine(RiskManager.RiskLimits riskLimits, int ringBufferSize) {
        // Initialize services
        this.orderManager = new OrderManager();
        this.positionManager = new PositionManager();
        this.riskManager = new RiskManager(positionManager, riskLimits);
        this.orderMetrics = new OrderMetrics();

        // Initialize handlers
        this.orderHandler = new OrderHandler(orderManager);
        this.orderHandler.setFillCallback((order, qty, price) ->
                onOrderFilled(order, qty, price));
        this.positionHandler = new PositionHandler(positionManager);
        this.riskHandler = new RiskHandler(riskManager);
        this.metricsHandler = new MetricsHandler(orderMetrics);

        // Create disruptor with busy-spin wait strategy for lowest latency
        this.disruptor = new Disruptor<>(
                TradingEventFactory.INSTANCE,
                ringBufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                new BusySpinWaitStrategy()
        );

        // Configure handler chain:
        // 1. Risk handler checks pre-trade limits
        // 2. Order handler submits orders to exchange
        // 3. Position handler updates positions on fills
        // 4. Metrics handler records statistics
        disruptor.handleEventsWith(riskHandler)
                .then(orderHandler)
                .then(positionHandler)
                .then(metricsHandler);

        this.ringBuffer = disruptor.getRingBuffer();

        log.info("TradingEngine initialized with ring buffer size: {}", ringBufferSize);
    }

    /**
     * Starts the trading engine.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            startTimeMillis = System.currentTimeMillis();
            disruptor.start();
            riskManager.enableTrading();
            log.info("TradingEngine started");
        }
    }

    /**
     * Stops the trading engine gracefully.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            startTimeMillis = 0;
            riskManager.disableTradingWithReason("Engine shutdown");
            disruptor.shutdown();
            log.info("TradingEngine stopped");
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
     * Performs pre-trade risk check before publishing to ring buffer.
     *
     * @return rejection reason if rejected, null if accepted
     */
    public String submitOrder(Order order) {
        if (!running.get()) {
            return "Trading engine not running";
        }

        // Pre-trade risk check (synchronous for immediate rejection)
        String rejection = riskManager.checkPreTradeRisk(order);
        if (rejection != null) {
            orderManager.rejectOrder(order, rejection);
            return rejection;
        }

        // Publish to ring buffer for async processing
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
     * Publishes an order acceptance event (from exchange callback).
     */
    public void onOrderAccepted(Order order) {
        ringBuffer.publishEvent(ORDER_ACCEPTED_TRANSLATOR, order);
    }

    /**
     * Publishes an order fill event (from exchange callback).
     */
    public void onOrderFilled(Order order, long fillQuantity, long fillPrice) {
        ringBuffer.publishEvent((event, sequence) ->
            event.populateOrderFilled(order, fillQuantity, fillPrice));
    }

    /**
     * Publishes an order rejection event (from exchange callback).
     */
    public void onOrderRejected(Order order, String reason) {
        ringBuffer.publishEvent((event, sequence) ->
            event.populateOrderRejected(order, reason));
    }

    /**
     * Publishes an order cancellation event (from exchange callback).
     */
    public void onOrderCancelled(Order order) {
        ringBuffer.publishEvent((event, sequence) ->
            event.populateOrderCancelled(order));
    }

    /**
     * Publishes a quote update event (from market data feed).
     */
    public void onQuoteUpdate(Quote quote) {
        ringBuffer.publishEvent(QUOTE_TRANSLATOR, quote);
    }

    /**
     * Publishes a trade update event (from market data feed).
     */
    public void onTradeUpdate(Trade trade) {
        ringBuffer.publishEvent(TRADE_TRANSLATOR, trade);
    }

    // Service accessors

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public PositionManager getPositionManager() {
        return positionManager;
    }

    public RiskManager getRiskManager() {
        return riskManager;
    }

    public OrderMetrics getOrderMetrics() {
        return orderMetrics;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets remaining capacity in the ring buffer.
     */
    public long getRemainingCapacity() {
        return ringBuffer.remainingCapacity();
    }

    /**
     * Gets current cursor position (events published).
     */
    public long getCursor() {
        return ringBuffer.getCursor();
    }

    /**
     * Resets daily counters (call at start of trading day).
     */
    public void resetDailyCounters() {
        riskManager.resetDailyCounters();
        orderMetrics.reset();
        log.info("Daily counters reset");
    }

    /**
     * Gets a snapshot of current engine state.
     */
    public EngineSnapshot getSnapshot() {
        long start = startTimeMillis;
        long uptime = start > 0 ? System.currentTimeMillis() - start : 0;
        return new EngineSnapshot(
                running.get(),
                start,
                uptime,
                ringBuffer.getCursor(),
                ringBuffer.remainingCapacity(),
                orderManager.getOrderCount(),
                orderManager.getActiveOrders().size(),
                positionManager.getSnapshot(),
                orderMetrics.snapshot(),
                riskManager.isTradingEnabled(),
                riskManager.getDisabledReason()
        );
    }

    /**
     * Immutable snapshot of engine state.
     */
    public record EngineSnapshot(
            boolean running,
            long startTimeMillis,
            long uptimeMillis,
            long eventsPublished,
            long ringBufferCapacity,
            int totalOrders,
            int activeOrders,
            PositionManager.PositionSnapshot positions,
            OrderMetrics.MetricsSnapshot metrics,
            boolean tradingEnabled,
            String tradingDisabledReason
    ) {}
}
