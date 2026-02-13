package com.hft.bdd.backtest;

import com.hft.core.model.Symbol;
import com.hft.core.model.TradingPeriod;

/**
 * Configuration for a single backtest run.
 *
 * @param symbol           The trading symbol
 * @param strategyType     Strategy type identifier (e.g., "ema_adx_rsi")
 * @param periodFilter     If non-null, only trade during this period; null = all periods
 * @param initialCapital   Starting portfolio value in dollars
 * @param reinvestProfits  true = compound returns; false = reset to initialCapital each day
 */
public record BacktestConfig(
        Symbol symbol,
        String strategyType,
        TradingPeriod periodFilter,
        double initialCapital,
        boolean reinvestProfits
) {
    public BacktestConfig(Symbol symbol, String strategyType, double initialCapital, boolean reinvestProfits) {
        this(symbol, strategyType, null, initialCapital, reinvestProfits);
    }

    public String label() {
        String period = periodFilter != null ? periodFilter.name() : "ALL_PERIODS";
        String mode = reinvestProfits ? "compound" : "daily_reset";
        return String.format("%s_%s_%s_%s", strategyType, symbol.getTicker(), period, mode);
    }
}
