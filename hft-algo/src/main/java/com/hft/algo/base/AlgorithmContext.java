package com.hft.algo.base;

import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;
import com.hft.core.model.Trade;

import java.util.function.Consumer;

/**
 * Context provided to algorithms for market data and order submission.
 */
public interface AlgorithmContext {

    /**
     * Gets the current quote for a symbol.
     */
    Quote getQuote(Symbol symbol);

    /**
     * Gets the current timestamp in nanoseconds.
     */
    long getCurrentTimeNanos();

    /**
     * Gets the current timestamp in milliseconds.
     */
    long getCurrentTimeMillis();

    /**
     * Submits an order request.
     */
    void submitOrder(OrderRequest request);

    /**
     * Cancels an existing order.
     */
    void cancelOrder(long clientOrderId);

    /**
     * Registers a callback for fill notifications.
     */
    void onFill(Consumer<Trade> callback);

    /**
     * Gets historical volume for a symbol (for VWAP calculations).
     * Returns array of volume by time bucket.
     */
    long[] getHistoricalVolume(Symbol symbol, int buckets);

    /**
     * Logs an info message.
     */
    void logInfo(String message);

    /**
     * Logs an error message.
     */
    void logError(String message, Throwable error);
}
