package com.hft.api.service;

import com.hft.algo.base.AlgorithmState;
import com.hft.algo.base.StrategyParameters;
import com.hft.algo.base.TradingStrategy;
import com.hft.algo.strategy.MeanReversionStrategy;
import com.hft.algo.strategy.MomentumStrategy;
import com.hft.api.dto.*;
import com.hft.core.model.*;
import com.hft.engine.TradingEngine;
import com.hft.engine.service.PositionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TradingService {
    private static final Logger log = LoggerFactory.getLogger(TradingService.class);

    private final TradingEngine tradingEngine;
    private final Map<String, TradingStrategy> activeStrategies = new ConcurrentHashMap<>();

    public TradingService() {
        this.tradingEngine = new TradingEngine();
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
        return EngineStatusDto.from(tradingEngine.getSnapshot());
    }

    public void resetDailyCounters() {
        tradingEngine.resetDailyCounters();
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
        }

        return OrderDto.from(order);
    }

    public void cancelOrder(long clientOrderId, String symbolTicker, String exchangeName) {
        Symbol symbol = new Symbol(symbolTicker, Exchange.valueOf(exchangeName));
        tradingEngine.cancelOrder(clientOrderId, symbol);
    }

    public List<OrderDto> getActiveOrders() {
        return tradingEngine.getOrderManager().getActiveOrders().stream()
                .map(OrderDto::from)
                .collect(Collectors.toList());
    }

    public Optional<OrderDto> getOrder(long clientOrderId) {
        Order order = tradingEngine.getOrderManager().getOrder(clientOrderId);
        return order != null ? Optional.of(OrderDto.from(order)) : Optional.empty();
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
        Set<Symbol> symbols = request.symbols().stream()
                .map(s -> new Symbol(s, Exchange.valueOf(request.exchange())))
                .collect(Collectors.toSet());

        StrategyParameters params = new StrategyParameters();
        if (request.parameters() != null) {
            request.parameters().forEach(params::set);
        }

        TradingStrategy strategy;
        switch (request.type().toLowerCase()) {
            case "momentum" -> strategy = new MomentumStrategy(symbols, params);
            case "meanreversion", "mean_reversion" -> strategy = new MeanReversionStrategy(symbols, params);
            default -> throw new IllegalArgumentException("Unknown strategy type: " + request.type());
        }

        activeStrategies.put(strategy.getId(), strategy);
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
        log.info("Deleted strategy: {}", id);
    }

    private StrategyDto toStrategyDto(TradingStrategy strategy) {
        List<String> symbolList = strategy.getSymbols().stream()
                .map(Symbol::getTicker)
                .collect(Collectors.toList());

        StrategyDto.StrategyStatsDto stats = new StrategyDto.StrategyStatsDto(
                0, 0, 0, 0, 0, 0,
                strategy.getRealizedPnl(),
                strategy.getUnrealizedPnl(),
                strategy.getMaxDrawdown()
        );

        return new StrategyDto(
                strategy.getId(),
                strategy.getName(),
                strategy.getName(),
                strategy.getState(),
                symbolList,
                strategy.getParameters().toMap(),
                strategy.getProgress(),
                stats
        );
    }

    // Risk operations

    public void enableTrading() {
        tradingEngine.getRiskManager().enableTrading();
    }

    public void disableTrading(String reason) {
        tradingEngine.getRiskManager().disableTradingWithReason(reason);
    }

    public boolean isTradingEnabled() {
        return tradingEngine.getRiskManager().isTradingEnabled();
    }

    // Access to engine for WebSocket
    public TradingEngine getTradingEngine() {
        return tradingEngine;
    }
}
