package com.hft.api.service;

import com.hft.algo.base.AlgorithmContext;
import com.hft.algo.base.OrderRequest;
import com.hft.core.model.*;
import com.hft.engine.TradingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * AlgorithmContext implementation that bridges to the TradingEngine.
 * Provides algorithms with market data access and order submission capabilities.
 */
public class TradingEngineAlgorithmContext implements AlgorithmContext {
    private static final Logger log = LoggerFactory.getLogger(TradingEngineAlgorithmContext.class);

    private final TradingEngine tradingEngine;
    private final Map<Symbol, Quote> latestQuotes = new ConcurrentHashMap<>();
    private Consumer<Trade> fillCallback;

    public TradingEngineAlgorithmContext(TradingEngine tradingEngine) {
        this.tradingEngine = tradingEngine;
    }

    @Override
    public Quote getQuote(Symbol symbol) {
        return latestQuotes.get(symbol);
    }

    /**
     * Updates the latest quote for a symbol.
     * Called when market data is received.
     */
    public void updateQuote(Symbol symbol, Quote quote) {
        latestQuotes.put(symbol, quote);
    }

    @Override
    public long getCurrentTimeNanos() {
        return System.nanoTime();
    }

    @Override
    public long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public void submitOrder(OrderRequest request) {
        Order order = new Order();
        order.setSymbol(request.getSymbol());
        order.setSide(request.getSide());
        order.setType(request.getType());
        order.setQuantity(request.getQuantity());
        order.setPrice(request.getPrice());
        order.setTimeInForce(request.getTimeInForce());
        if (request.getAlgorithmId() != null) {
            order.strategyId(request.getAlgorithmId());
        }

        String rejection = tradingEngine.submitOrder(order);
        if (rejection != null) {
            log.warn("Order rejected by engine: {}", rejection);
        }
    }

    @Override
    public void cancelOrder(long clientOrderId) {
        Order order = tradingEngine.getOrderManager().getOrder(clientOrderId);
        if (order != null) {
            tradingEngine.cancelOrder(clientOrderId, order.getSymbol());
        }
    }

    @Override
    public void onFill(Consumer<Trade> callback) {
        this.fillCallback = callback;
    }

    /**
     * Notifies the algorithm of a trade fill.
     * Called when a fill is received from the exchange.
     */
    public void notifyFill(Trade trade) {
        if (fillCallback != null) {
            fillCallback.accept(trade);
        }
    }

    @Override
    public long[] getHistoricalVolume(Symbol symbol, int buckets) {
        // Return uniform distribution for now - could be enhanced with actual historical data
        long[] volumes = new long[buckets];
        Arrays.fill(volumes, 1000L);
        return volumes;
    }

    @Override
    public void logInfo(String message) {
        log.info("[Algorithm] {}", message);
    }

    @Override
    public void logError(String message, Throwable error) {
        log.error("[Algorithm] {}", message, error);
    }
}
