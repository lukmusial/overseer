package com.hft.algo.base;

import com.hft.core.model.Quote;
import com.hft.core.model.Trade;

/**
 * Base interface for all trading algorithms.
 * Algorithms can be either execution algorithms (VWAP, TWAP) or
 * trading strategies (Momentum, Mean Reversion).
 */
public interface TradingAlgorithm {

    /**
     * Gets the unique identifier for this algorithm instance.
     */
    String getId();

    /**
     * Gets the algorithm type name.
     */
    String getName();

    /**
     * Gets the current state of the algorithm.
     */
    AlgorithmState getState();

    /**
     * Initializes the algorithm with context.
     */
    void initialize(AlgorithmContext context);

    /**
     * Starts the algorithm.
     */
    void start();

    /**
     * Pauses the algorithm (can be resumed).
     */
    void pause();

    /**
     * Resumes a paused algorithm.
     */
    void resume();

    /**
     * Stops and cancels the algorithm.
     */
    void cancel();

    /**
     * Called when a quote update is received.
     */
    void onQuote(Quote quote);

    /**
     * Called when a fill is received.
     */
    void onFill(Trade fill);

    /**
     * Called periodically (e.g., every second) to allow time-based logic.
     */
    void onTimer(long timestampNanos);

    /**
     * Gets progress as percentage (0-100).
     */
    double getProgress();

    /**
     * Gets statistics for this algorithm.
     */
    AlgorithmStats getStats();
}
