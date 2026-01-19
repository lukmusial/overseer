package com.hft.algo.base;

import com.hft.core.model.OrderSide;
import com.hft.core.model.Symbol;

/**
 * Base interface for execution algorithms.
 * Execution algorithms are designed to execute a parent order
 * by slicing it into smaller child orders over time.
 *
 * Examples: VWAP, TWAP, Implementation Shortfall
 */
public interface ExecutionAlgorithm extends TradingAlgorithm {

    /**
     * Gets the symbol being executed.
     */
    Symbol getSymbol();

    /**
     * Gets the side of the parent order.
     */
    OrderSide getSide();

    /**
     * Gets the total target quantity to execute.
     */
    long getTargetQuantity();

    /**
     * Gets the quantity filled so far.
     */
    long getFilledQuantity();

    /**
     * Gets the remaining quantity to fill.
     */
    default long getRemainingQuantity() {
        return getTargetQuantity() - getFilledQuantity();
    }

    /**
     * Gets the volume-weighted average fill price.
     */
    long getAverageFillPrice();

    /**
     * Checks if execution is complete.
     */
    default boolean isComplete() {
        return getFilledQuantity() >= getTargetQuantity();
    }

    /**
     * Gets the limit price (0 for market orders).
     */
    long getLimitPrice();

    /**
     * Gets the start time in nanoseconds.
     */
    long getStartTimeNanos();

    /**
     * Gets the end time (deadline) in nanoseconds.
     */
    long getEndTimeNanos();
}
