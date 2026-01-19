package com.hft.algo.strategy;

import com.hft.algo.base.AbstractTradingStrategy;
import com.hft.algo.base.StrategyParameters;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;

import java.util.*;

/**
 * Momentum trading strategy.
 *
 * This strategy follows price trends by buying assets that have been
 * rising and selling (or shorting) assets that have been falling.
 *
 * Key features:
 * - Uses exponential moving averages (EMA) to detect trends
 * - Generates signals based on short-term vs long-term EMA crossovers
 * - Includes momentum strength threshold to filter weak signals
 * - Supports configurable lookback periods and position sizing
 *
 * Parameters:
 * - shortPeriod: Short EMA period (default: 10)
 * - longPeriod: Long EMA period (default: 30)
 * - signalThreshold: Minimum signal strength to trade (default: 0.02)
 * - maxPositionSize: Maximum position per symbol (default: 1000)
 */
public class MomentumStrategy extends AbstractTradingStrategy {

    private static final String NAME = "Momentum";

    // EMA tracking
    private final Map<Symbol, Double> shortEma = new HashMap<>();
    private final Map<Symbol, Double> longEma = new HashMap<>();
    private final Map<Symbol, Long> priceHistory = new HashMap<>();

    // Parameters
    private int shortPeriod;
    private int longPeriod;
    private double signalThreshold;
    private long maxPositionSize;

    // EMA multipliers
    private double shortMultiplier;
    private double longMultiplier;

    public MomentumStrategy(Set<Symbol> symbols, StrategyParameters parameters) {
        super(symbols, parameters);
        loadParameters();
    }

    public MomentumStrategy(Set<Symbol> symbols) {
        this(symbols, defaultParameters());
    }

    private static StrategyParameters defaultParameters() {
        return new StrategyParameters()
                .set("shortPeriod", 10)
                .set("longPeriod", 30)
                .set("signalThreshold", 0.02)
                .set("maxPositionSize", 1000L);
    }

    private void loadParameters() {
        this.shortPeriod = parameters.getInt("shortPeriod", 10);
        this.longPeriod = parameters.getInt("longPeriod", 30);
        this.signalThreshold = parameters.getDouble("signalThreshold", 0.02);
        this.maxPositionSize = parameters.getLong("maxPositionSize", 1000);

        // Calculate EMA multipliers
        this.shortMultiplier = 2.0 / (shortPeriod + 1);
        this.longMultiplier = 2.0 / (longPeriod + 1);
    }

    @Override
    protected void onParametersUpdated() {
        loadParameters();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected double calculateSignal(Symbol symbol, Quote quote) {
        long price = (quote.getBidPrice() + quote.getAskPrice()) / 2;

        // Initialize EMAs if needed
        if (!shortEma.containsKey(symbol)) {
            shortEma.put(symbol, (double) price);
            longEma.put(symbol, (double) price);
            priceHistory.put(symbol, price);
            return 0.0;
        }

        // Update EMAs
        double prevShort = shortEma.get(symbol);
        double prevLong = longEma.get(symbol);

        double newShort = (price - prevShort) * shortMultiplier + prevShort;
        double newLong = (price - prevLong) * longMultiplier + prevLong;

        shortEma.put(symbol, newShort);
        longEma.put(symbol, newLong);
        priceHistory.put(symbol, price);

        // Calculate signal based on EMA crossover
        // Signal = (shortEMA - longEMA) / longEMA
        if (newLong == 0) {
            return 0.0;
        }

        double rawSignal = (newShort - newLong) / newLong;

        // Normalize to -1 to 1 range
        // Assuming typical crossover range of -5% to +5%
        double normalizedSignal = Math.max(-1.0, Math.min(1.0, rawSignal / 0.05));

        // Apply threshold - only generate signal if strong enough
        if (Math.abs(normalizedSignal) < signalThreshold / 0.05) {
            return 0.0;
        }

        return normalizedSignal;
    }

    @Override
    protected long calculateTargetPosition(Symbol symbol, double signal) {
        if (Math.abs(signal) < 0.01) {
            // Weak signal - no position
            return 0;
        }

        // Scale position by signal strength
        long targetSize = (long) (maxPositionSize * Math.abs(signal));

        // Direction based on signal sign
        if (signal > 0) {
            return targetSize; // Long
        } else {
            return -targetSize; // Short
        }
    }

    /**
     * Gets the current short EMA value for a symbol.
     */
    public double getShortEma(Symbol symbol) {
        return shortEma.getOrDefault(symbol, 0.0);
    }

    /**
     * Gets the current long EMA value for a symbol.
     */
    public double getLongEma(Symbol symbol) {
        return longEma.getOrDefault(symbol, 0.0);
    }

    /**
     * Creates a builder for MomentumStrategy.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Set<Symbol> symbols = new HashSet<>();
        private final StrategyParameters parameters = new StrategyParameters();

        public Builder addSymbol(Symbol symbol) {
            symbols.add(symbol);
            return this;
        }

        public Builder addSymbols(Collection<Symbol> symbols) {
            this.symbols.addAll(symbols);
            return this;
        }

        public Builder shortPeriod(int period) {
            parameters.set("shortPeriod", period);
            return this;
        }

        public Builder longPeriod(int period) {
            parameters.set("longPeriod", period);
            return this;
        }

        public Builder signalThreshold(double threshold) {
            parameters.set("signalThreshold", threshold);
            return this;
        }

        public Builder maxPositionSize(long size) {
            parameters.set("maxPositionSize", size);
            return this;
        }

        public MomentumStrategy build() {
            if (symbols.isEmpty()) {
                throw new IllegalStateException("At least one symbol is required");
            }
            return new MomentumStrategy(symbols, parameters);
        }
    }
}
