package com.hft.api.service;

import com.hft.algo.base.AbstractTradingStrategy;
import com.hft.algo.base.AlgorithmState;
import com.hft.algo.base.StrategyParameters;
import com.hft.algo.base.TradingStrategy;
import com.hft.algo.strategy.MeanReversionStrategy;
import com.hft.algo.strategy.MomentumStrategy;
import com.hft.algo.strategy.TwapStrategy;
import com.hft.algo.strategy.VwapStrategy;
import com.hft.api.config.RiskLimitsProperties;
import com.hft.api.dto.*;
import com.hft.core.model.*;
import com.hft.engine.TradingEngine;
import com.hft.engine.service.PositionManager;
import com.hft.engine.service.RiskManager;
import com.hft.persistence.PersistenceManager;
import com.hft.persistence.PositionSnapshotStore;
import com.hft.persistence.StrategyRepository;
import com.hft.persistence.StrategyRepository.StrategyDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TradingService {
    private static final Logger log = LoggerFactory.getLogger(TradingService.class);

    private final TradingEngine tradingEngine;
    private final TradingEngineAlgorithmContext algorithmContext;
    private final PersistenceManager persistenceManager;
    private final Map<String, TradingStrategy> activeStrategies = new ConcurrentHashMap<>();

    /**
     * Spring-managed constructor with risk limits from configuration.
     */
    @Autowired
    public TradingService(RiskLimitsProperties riskLimitsProperties) {
        this(PersistenceManager.chronicle(), riskLimitsProperties.toRiskLimits());
    }

    /**
     * Constructor for testing - allows injecting custom persistence and risk limits.
     */
    public TradingService(PersistenceManager persistenceManager, RiskManager.RiskLimits riskLimits) {
        this.tradingEngine = new TradingEngine(riskLimits);
        this.algorithmContext = new TradingEngineAlgorithmContext(tradingEngine);
        this.persistenceManager = persistenceManager;
        registerOrderPersistenceListener();
        registerPositionPersistenceListener();
    }

    /**
     * Constructor for testing with default risk limits.
     */
    public TradingService(PersistenceManager persistenceManager) {
        this(persistenceManager, RiskManager.RiskLimits.defaults());
    }

    @PostConstruct
    public void init() {
        loadPersistedPositions();
        loadPersistedOrders();
        loadPersistedStrategies();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down TradingService, flushing persistence...");
        persistenceManager.flush();
        persistenceManager.close();
    }

    private void registerOrderPersistenceListener() {
        tradingEngine.getOrderManager().addOrderListener(order -> {
            try {
                persistenceManager.saveOrder(order);
            } catch (Exception e) {
                log.error("Failed to persist order {}: {}", order.getClientOrderId(), e.getMessage());
            }
        });
    }

    private void registerPositionPersistenceListener() {
        tradingEngine.getPositionManager().addPositionListener(position -> {
            try {
                persistenceManager.getPositionStore().saveSnapshot(position, System.nanoTime());
            } catch (Exception e) {
                log.error("Failed to persist position {}: {}", position.getSymbol(), e.getMessage());
            }
        });
    }

    private void loadPersistedPositions() {
        Map<Symbol, PositionSnapshotStore.PositionSnapshot> snapshots =
                persistenceManager.getPositionStore().getAllLatestSnapshots();
        if (snapshots.isEmpty()) {
            return;
        }
        log.info("Restoring {} persisted positions", snapshots.size());
        for (Map.Entry<Symbol, PositionSnapshotStore.PositionSnapshot> entry : snapshots.entrySet()) {
            PositionSnapshotStore.PositionSnapshot snapshot = entry.getValue();
            if (snapshot.quantity() != 0) {
                tradingEngine.getPositionManager().restorePosition(
                        entry.getKey(),
                        snapshot.quantity(),
                        snapshot.averageEntryPrice(),
                        snapshot.totalCost(),
                        snapshot.realizedPnl(),
                        snapshot.marketValue(),
                        snapshot.currentPrice(),
                        snapshot.priceScale(),
                        snapshot.openedAt()
                );
            }
        }
    }

    private void loadPersistedOrders() {
        List<Order> orders = persistenceManager.getOrderRepository().findAll();
        if (orders.isEmpty()) {
            return;
        }
        log.info("Loading {} persisted orders", orders.size());
        for (Order order : orders) {
            tradingEngine.getOrderManager().trackOrder(order);
        }
    }

    private void loadPersistedStrategies() {
        List<StrategyDefinition> definitions = persistenceManager.getStrategyRepository().findAll();
        log.info("Loading {} persisted strategies", definitions.size());

        for (StrategyDefinition def : definitions) {
            try {
                TradingStrategy strategy = createStrategyFromDefinition(def);
                strategy.initialize(algorithmContext);
                activeStrategies.put(strategy.getId(), strategy);
                log.info("Restored strategy: {} ({})", strategy.getId(), strategy.getName());
            } catch (Exception e) {
                log.error("Failed to restore strategy {}: {}", def.id(), e.getMessage());
            }
        }
    }

    private TradingStrategy createStrategyFromDefinition(StrategyDefinition def) {
        Set<Symbol> symbols = def.symbols().stream()
                .map(s -> new Symbol(s, Exchange.valueOf(def.exchange())))
                .collect(Collectors.toSet());

        StrategyParameters params = new StrategyParameters();
        if (def.parameters() != null) {
            def.parameters().forEach(params::set);
        }

        return switch (def.type().toLowerCase()) {
            case "momentum" -> new MomentumStrategy(symbols, params, def.name());
            case "meanreversion", "mean_reversion" -> new MeanReversionStrategy(symbols, params, def.name());
            case "vwap" -> new VwapStrategy(symbols, params, def.name());
            case "twap" -> new TwapStrategy(symbols, params, def.name());
            default -> throw new IllegalArgumentException("Unknown strategy type: " + def.type());
        };
    }

    private void persistStrategy(TradingStrategy strategy) {
        List<String> symbols = strategy.getSymbols().stream()
                .map(Symbol::getTicker)
                .collect(Collectors.toList());

        String exchange = strategy.getSymbols().stream()
                .findFirst()
                .map(s -> s.getExchange().name())
                .orElse("ALPACA");

        StrategyDefinition def = new StrategyDefinition(
                strategy.getId(),
                strategy instanceof AbstractTradingStrategy abs ? abs.getCustomName() : strategy.getName(),
                strategy.getName(),  // type
                symbols,
                exchange,
                strategy.getParameters().toMap(),
                strategy.getState().name()
        );

        persistenceManager.getStrategyRepository().save(def);
    }

    // Engine operations

    public void startEngine() {
        tradingEngine.start();
        log.info("Trading engine started");
    }

    public void stopEngine() {
        tradingEngine.stop();
        log.info("Trading engine stopped");
    }

    public EngineStatusDto getEngineStatus() {
        int runningStrategies = (int) activeStrategies.values().stream()
                .filter(s -> s.getState() == AlgorithmState.RUNNING)
                .count();
        return EngineStatusDto.from(tradingEngine.getSnapshot(), runningStrategies);
    }

    public void resetDailyCounters() {
        tradingEngine.resetDailyCounters();
    }

    private String resolveStrategyName(String strategyId) {
        if (strategyId == null) return null;
        TradingStrategy strategy = activeStrategies.get(strategyId);
        if (strategy == null) return null;
        if (strategy instanceof AbstractTradingStrategy abs) {
            return abs.getDisplayName();
        }
        return strategy.getName();
    }

    public OrderDto toOrderDto(Order order) {
        return OrderDto.from(order, resolveStrategyName(order.getStrategyId()));
    }

    // Order operations

    public OrderDto submitOrder(CreateOrderRequest request) {
        Symbol symbol = new Symbol(request.symbol(), Exchange.valueOf(request.exchange()));

        Order order = new Order();
        order.setSymbol(symbol);
        order.setSide(request.side());
        order.setType(request.type());
        order.setQuantity(request.quantity());
        order.setPrice(request.price());
        order.setStopPrice(request.stopPrice());
        if (request.timeInForce() != null) {
            order.setTimeInForce(request.timeInForce());
        }
        if (request.strategyId() != null) {
            order.strategyId(request.strategyId());
        }

        String rejection = tradingEngine.submitOrder(order);
        if (rejection != null) {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectReason(rejection);
            // Persist rejected orders that bypassed OrderManager (e.g., engine not running)
            persistenceManager.saveOrder(order);
        }

        return toOrderDto(order);
    }

    public void cancelOrder(long clientOrderId, String symbolTicker, String exchangeName) {
        Symbol symbol = new Symbol(symbolTicker, Exchange.valueOf(exchangeName));
        tradingEngine.cancelOrder(clientOrderId, symbol);
    }

    public List<OrderDto> getActiveOrders() {
        return tradingEngine.getOrderManager().getActiveOrders().stream()
                .map(this::toOrderDto)
                .collect(Collectors.toList());
    }

    public List<OrderDto> getAllOrders() {
        return tradingEngine.getOrderManager().getOrders().stream()
                .map(this::toOrderDto)
                .collect(Collectors.toList());
    }

    /**
     * Returns the most recent N orders regardless of status, sorted by creation time descending.
     */
    public List<OrderDto> getRecentOrders(int limit) {
        List<Order> allOrders = new ArrayList<>(tradingEngine.getOrderManager().getOrders());
        allOrders.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return allOrders.stream()
                .limit(limit)
                .map(this::toOrderDto)
                .collect(Collectors.toList());
    }

    /**
     * Searches orders with optional filters for strategy, status, and symbol.
     */
    public List<OrderDto> searchOrders(String strategyId, String status, String symbol, int limit, int offset) {
        Stream<Order> stream = tradingEngine.getOrderManager().getOrders().stream();

        if (strategyId != null && !strategyId.isEmpty()) {
            stream = stream.filter(o -> strategyId.equals(o.getStrategyId()));
        }
        if (status != null && !status.isEmpty()) {
            stream = stream.filter(o -> status.equals(o.getStatus().name()));
        }
        if (symbol != null && !symbol.isEmpty()) {
            final String symbolLower = symbol.toLowerCase();
            stream = stream.filter(o -> o.getSymbol() != null &&
                    o.getSymbol().getTicker().toLowerCase().contains(symbolLower));
        }

        return stream
                .sorted((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()))
                .skip(offset)
                .limit(limit)
                .map(this::toOrderDto)
                .collect(Collectors.toList());
    }

    /**
     * Dispatches a fill event to the strategy that owns the order.
     */
    public void dispatchFillToStrategy(Order order) {
        if (order.getStrategyId() == null) {
            return;
        }

        TradingStrategy strategy = activeStrategies.get(order.getStrategyId());
        if (strategy != null) {
            Trade trade = new Trade();
            trade.setSymbol(order.getSymbol());
            trade.setSide(order.getSide());
            trade.setQuantity(order.getFilledQuantity());
            trade.setPrice(order.getAverageFilledPrice());
            strategy.onFill(trade);
        }
    }

    public Optional<OrderDto> getOrder(long clientOrderId) {
        Order order = tradingEngine.getOrderManager().getOrder(clientOrderId);
        return order != null ? Optional.of(toOrderDto(order)) : Optional.empty();
    }

    // Position operations

    public List<PositionDto> getAllPositions() {
        return tradingEngine.getPositionManager().getAllPositions().stream()
                .map(PositionDto::from)
                .collect(Collectors.toList());
    }

    public List<PositionDto> getActivePositions() {
        return tradingEngine.getPositionManager().getActivePositions().stream()
                .map(PositionDto::from)
                .collect(Collectors.toList());
    }

    public Optional<PositionDto> getPosition(String symbolTicker, String exchangeName) {
        Symbol symbol = new Symbol(symbolTicker, Exchange.valueOf(exchangeName));
        Position position = tradingEngine.getPositionManager().getPosition(symbol);
        return position != null ? Optional.of(PositionDto.from(position)) : Optional.empty();
    }

    public PositionManager.PositionSnapshot getPositionSnapshot() {
        return tradingEngine.getPositionManager().getSnapshot();
    }

    // Strategy operations

    public StrategyDto createStrategy(CreateStrategyRequest request) {
        // Check for duplicate name
        String customName = request.name();
        if (customName != null && !customName.isBlank()) {
            boolean nameExists = activeStrategies.values().stream()
                    .filter(s -> s instanceof AbstractTradingStrategy)
                    .map(s -> ((AbstractTradingStrategy) s).getDisplayName())
                    .anyMatch(name -> customName.equalsIgnoreCase(name));
            if (nameExists) {
                throw new IllegalArgumentException("Strategy name already exists: " + customName);
            }
        }

        Set<Symbol> symbols = request.symbols().stream()
                .map(s -> new Symbol(s, Exchange.valueOf(request.exchange())))
                .collect(Collectors.toSet());

        StrategyParameters params = new StrategyParameters();
        if (request.parameters() != null) {
            request.parameters().forEach(params::set);
        }

        TradingStrategy strategy;
        switch (request.type().toLowerCase()) {
            case "momentum" -> strategy = new MomentumStrategy(symbols, params, customName);
            case "meanreversion", "mean_reversion" -> strategy = new MeanReversionStrategy(symbols, params, customName);
            case "vwap" -> strategy = new VwapStrategy(symbols, params, customName);
            case "twap" -> strategy = new TwapStrategy(symbols, params, customName);
            default -> throw new IllegalArgumentException("Unknown strategy type: " + request.type());
        }

        // Initialize strategy with context before storing
        strategy.initialize(algorithmContext);
        activeStrategies.put(strategy.getId(), strategy);

        // Persist strategy definition
        persistStrategy(strategy);

        log.info("Created strategy: {} ({})", strategy.getId(), strategy.getName());

        return toStrategyDto(strategy);
    }

    public List<StrategyDto> getStrategies() {
        return activeStrategies.values().stream()
                .map(this::toStrategyDto)
                .collect(Collectors.toList());
    }

    public Optional<StrategyDto> getStrategy(String id) {
        TradingStrategy strategy = activeStrategies.get(id);
        return strategy != null ? Optional.of(toStrategyDto(strategy)) : Optional.empty();
    }

    public void startStrategy(String id) {
        TradingStrategy strategy = activeStrategies.get(id);
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy not found: " + id);
        }
        strategy.start();
        log.info("Started strategy: {}", id);
    }

    public void stopStrategy(String id) {
        TradingStrategy strategy = activeStrategies.get(id);
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy not found: " + id);
        }
        strategy.cancel();
        log.info("Stopped strategy: {}", id);
    }

    public void deleteStrategy(String id) {
        TradingStrategy strategy = activeStrategies.remove(id);
        if (strategy != null && strategy.getState() == AlgorithmState.RUNNING) {
            strategy.cancel();
        }

        // Remove from persistence
        persistenceManager.getStrategyRepository().delete(id);

        log.info("Deleted strategy: {}", id);
    }

    /**
     * Deletes all strategies. Returns the count of deleted strategies.
     */
    public int deleteAllStrategies() {
        List<String> ids = new ArrayList<>(activeStrategies.keySet());
        for (String id : ids) {
            deleteStrategy(id);
        }
        log.info("Deleted all {} strategies", ids.size());
        return ids.size();
    }

    private StrategyDto toStrategyDto(TradingStrategy strategy) {
        List<String> symbolList = strategy.getSymbols().stream()
                .map(Symbol::getTicker)
                .collect(Collectors.toList());

        StrategyDto.StrategyStatsDto stats;
        if (strategy instanceof AbstractTradingStrategy abs) {
            stats = new StrategyDto.StrategyStatsDto(
                    abs.getStartTimeNanos(),
                    0,
                    abs.getOrdersSubmitted(),
                    abs.getFilledOrders(),
                    abs.getCancelledOrders(),
                    abs.getRejectedOrders(),
                    abs.getRealizedPnl(),
                    abs.getUnrealizedPnl(),
                    abs.getMaxDrawdown()
            );
        } else {
            stats = new StrategyDto.StrategyStatsDto(
                    0, 0, 0, 0, 0, 0,
                    strategy.getRealizedPnl(),
                    strategy.getUnrealizedPnl(),
                    strategy.getMaxDrawdown()
            );
        }

        // Get display name (custom name if set, otherwise type name)
        String displayName = strategy.getName();
        if (strategy instanceof AbstractTradingStrategy abstractStrategy) {
            displayName = abstractStrategy.getDisplayName();
        }

        // Determine priceScale from exchange (Binance=8 decimals, others=2)
        int priceScale = strategy.getSymbols().stream()
                .findFirst()
                .map(s -> s.getExchange() == Exchange.BINANCE ? 100_000_000 : 100)
                .orElse(100);

        return new StrategyDto(
                strategy.getId(),
                displayName,
                strategy.getName(),  // type is always the algorithm type
                strategy.getState(),
                symbolList,
                strategy.getParameters().toMap(),
                strategy.getProgress(),
                priceScale,
                stats
        );
    }

    // Risk operations

    /**
     * Returns current risk limits configuration and usage.
     */
    public RiskLimitsDto getRiskLimits() {
        RiskManager rm = tradingEngine.getRiskManager();
        long totalPnlCents = tradingEngine.getPositionManager().getTotalPnlCents();
        long netExposure = tradingEngine.getPositionManager().getNetExposure();
        return RiskLimitsDto.from(rm, totalPnlCents, netExposure);
    }

    /**
     * Updates risk limits dynamically.
     */
    public RiskLimitsDto updateRiskLimits(RiskManager.RiskLimits newLimits) {
        tradingEngine.getRiskManager().setLimits(newLimits);
        log.info("Risk limits updated via API");
        return getRiskLimits();
    }

    public void enableTrading() {
        tradingEngine.getRiskManager().enableTrading();
    }

    public void disableTrading(String reason) {
        tradingEngine.getRiskManager().disableTradingWithReason(reason);
    }

    public boolean isTradingEnabled() {
        return tradingEngine.getRiskManager().isTradingEnabled();
    }

    /**
     * Dispatches a quote to all active (RUNNING) strategies that trade the quote's symbol.
     * Also updates the AlgorithmContext quote cache so strategies can query latest quotes.
     */
    public void dispatchQuoteToStrategies(Quote quote) {
        // Update the algorithm context's quote cache
        algorithmContext.updateQuote(quote.getSymbol(), quote);

        // Dispatch to all running strategies
        for (TradingStrategy strategy : activeStrategies.values()) {
            if (strategy.getState() == AlgorithmState.RUNNING) {
                try {
                    strategy.onQuote(quote);
                } catch (Exception e) {
                    log.error("Error dispatching quote to strategy {}: {}", strategy.getId(), e.getMessage());
                }
            }
        }
    }

    // Access to engine for WebSocket
    public TradingEngine getTradingEngine() {
        return tradingEngine;
    }
}
