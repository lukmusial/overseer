package com.hft.algo.base;

import com.hft.core.model.Symbol;

import java.util.Set;

/**
 * Base interface for trading strategies.
 * Trading strategies generate signals and manage positions
 * based on market conditions and quantitative models.
 *
 * Examples: Momentum, Mean Reversion, Pairs Trading
 */
public interface TradingStrategy extends TradingAlgorithm {

    /**
     * Gets the symbols this strategy trades.
     */
    Set<Symbol> getSymbols();

    /**
     * Gets the current signal strength (-1.0 to 1.0).
     * Negative = bearish, Positive = bullish, 0 = neutral.
     */
    double getSignal(Symbol symbol);

    /**
     * Gets the target position for a symbol.
     */
    long getTargetPosition(Symbol symbol);

    /**
     * Gets the current position for a symbol.
     */
    long getCurrentPosition(Symbol symbol);

    /**
     * Gets total realized P&L for the strategy.
     */
    long getRealizedPnl();

    /**
     * Gets total unrealized P&L for the strategy.
     */
    long getUnrealizedPnl();

    /**
     * Gets maximum drawdown observed.
     */
    long getMaxDrawdown();

    /**
     * Gets the strategy parameters.
     */
    StrategyParameters getParameters();

    /**
     * Updates strategy parameters (if supported).
     */
    void updateParameters(StrategyParameters parameters);
}
