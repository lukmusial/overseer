package com.hft.risk;

import com.hft.core.model.Position;
import com.hft.core.model.Symbol;

/**
 * Context provided to risk rules for evaluation.
 * Contains current state needed for risk calculations.
 */
public interface RiskContext {

    /**
     * Gets the current position for a symbol.
     */
    Position getPosition(Symbol symbol);

    /**
     * Gets the total P&L across all positions.
     */
    long getTotalPnl();

    /**
     * Gets the total unrealized P&L.
     */
    long getUnrealizedPnl();

    /**
     * Gets the total realized P&L.
     */
    long getRealizedPnl();

    /**
     * Gets the net exposure (long - short).
     */
    long getNetExposure();

    /**
     * Gets the gross exposure (long + short).
     */
    long getGrossExposure();

    /**
     * Gets the number of orders submitted today.
     */
    long getOrdersSubmittedToday();

    /**
     * Gets the total notional traded today.
     */
    long getNotionalTradedToday();

    /**
     * Gets the risk limits configuration.
     */
    RiskLimits getLimits();
}
