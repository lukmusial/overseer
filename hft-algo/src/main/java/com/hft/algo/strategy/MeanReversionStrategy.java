package com.hft.algo.strategy;

import com.hft.algo.base.AbstractTradingStrategy;
import com.hft.algo.base.StrategyParameters;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;

import java.util.*;

/**
 * Mean Reversion trading strategy.
 *
 * This strategy bets on prices returning to their historical mean.
 * It buys when price is below the mean (expecting it to rise) and
 * sells when price is above the mean (expecting it to fall).
 *
 * Key features:
 * - Uses Bollinger Bands to define mean and standard deviation boundaries
 * - Generates signals based on Z-score (deviation from mean)
 * - Entry when price exceeds configurable standard deviation threshold
 * - Exit when price returns to mean
 *
 * Parameters:
 * - lookbackPeriod: Period for calculating mean/std (default: 20)
 * - entryZScore: Z-score threshold for entry (default: 2.0)
 * - exitZScore: Z-score threshold for exit (default: 0.5)
 * - maxPositionSize: Maximum position per symbol (default: 1000)
 */
public class MeanReversionStrategy extends AbstractTradingStrategy {

    private static final String NAME = "MeanReversion";

    // Price history for calculations
    private final Map<Symbol, LinkedList<Long>> priceHistories = new HashMap<>();

    // Statistics
    private final Map<Symbol, Double> means = new HashMap<>();
    private final Map<Symbol, Double> stdDevs = new HashMap<>();
    private final Map<Symbol, Double> zScores = new HashMap<>();

    // Parameters
    private int lookbackPeriod;
    private double entryZScore;
    private double exitZScore;
    private long maxPositionSize;

    public MeanReversionStrategy(Set<Symbol> symbols, StrategyParameters parameters) {
        super(symbols, parameters);
        loadParameters();

        // Initialize price histories
        for (Symbol symbol : symbols) {
            priceHistories.put(symbol, new LinkedList<>());
        }
    }

    public MeanReversionStrategy(Set<Symbol> symbols) {
        this(symbols, defaultParameters());
    }

    private static StrategyParameters defaultParameters() {
        return new StrategyParameters()
                .set("lookbackPeriod", 20)
                .set("entryZScore", 2.0)
                .set("exitZScore", 0.5)
                .set("maxPositionSize", 1000L);
    }

    private void loadParameters() {
        this.lookbackPeriod = parameters.getInt("lookbackPeriod", 20);
        this.entryZScore = parameters.getDouble("entryZScore", 2.0);
        this.exitZScore = parameters.getDouble("exitZScore", 0.5);
        this.maxPositionSize = parameters.getLong("maxPositionSize", 1000);
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

        // Add price to history
        LinkedList<Long> history = priceHistories.get(symbol);
        history.addLast(price);

        // Maintain lookback window
        while (history.size() > lookbackPeriod) {
            history.removeFirst();
        }

        // Need enough history to calculate statistics
        if (history.size() < lookbackPeriod) {
            return 0.0;
        }

        // Calculate mean
        double sum = 0;
        for (long p : history) {
            sum += p;
        }
        double mean = sum / history.size();
        means.put(symbol, mean);

        // Calculate standard deviation
        double sumSquaredDiff = 0;
        for (long p : history) {
            double diff = p - mean;
            sumSquaredDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSquaredDiff / history.size());
        stdDevs.put(symbol, stdDev);

        // Avoid division by zero
        if (stdDev < 0.0001) {
            zScores.put(symbol, 0.0);
            return 0.0;
        }

        // Calculate Z-score
        double zScore = (price - mean) / stdDev;
        zScores.put(symbol, zScore);

        // Generate signal based on Z-score
        // Positive Z-score (price above mean) -> negative signal (sell/short)
        // Negative Z-score (price below mean) -> positive signal (buy)
        double signal = -zScore / entryZScore; // Normalized by entry threshold

        // Clamp to -1 to 1 range
        signal = Math.max(-1.0, Math.min(1.0, signal));

        return signal;
    }

    @Override
    protected long calculateTargetPosition(Symbol symbol, double signal) {
        double zScore = zScores.getOrDefault(symbol, 0.0);
        long currentPosition = getCurrentPosition(symbol);

        // Entry logic: only enter if Z-score exceeds threshold
        if (currentPosition == 0) {
            if (Math.abs(zScore) < entryZScore) {
                return 0; // Don't enter - signal not strong enough
            }

            // Enter position opposite to Z-score direction
            long targetSize = (long) (maxPositionSize * Math.min(1.0, Math.abs(zScore) / entryZScore));
            if (zScore > 0) {
                return -targetSize; // Price above mean, short
            } else {
                return targetSize; // Price below mean, long
            }
        }

        // Exit logic: close position when Z-score returns to threshold
        if (Math.abs(zScore) < exitZScore) {
            return 0; // Exit - price returned to mean
        }

        // Check for stop-out if position is going against us
        // If we're long and Z-score becomes more negative (further below mean),
        // or if we're short and Z-score becomes more positive (further above mean),
        // we might want to add to the position (averaging down/up)
        if ((currentPosition > 0 && zScore < -entryZScore) ||
            (currentPosition < 0 && zScore > entryZScore)) {
            // Double down (with limit)
            long additionalSize = (long) (maxPositionSize * 0.5);
            if (currentPosition > 0) {
                return Math.min(currentPosition + additionalSize, maxPositionSize * 2);
            } else {
                return Math.max(currentPosition - additionalSize, -maxPositionSize * 2);
            }
        }

        // Hold current position
        return currentPosition;
    }

    /**
     * Gets the current mean price for a symbol.
     */
    public double getMean(Symbol symbol) {
        return means.getOrDefault(symbol, 0.0);
    }

    /**
     * Gets the current standard deviation for a symbol.
     */
    public double getStdDev(Symbol symbol) {
        return stdDevs.getOrDefault(symbol, 0.0);
    }

    /**
     * Gets the current Z-score for a symbol.
     */
    public double getZScore(Symbol symbol) {
        return zScores.getOrDefault(symbol, 0.0);
    }

    /**
     * Gets the upper Bollinger Band (mean + 2 * stdDev).
     */
    public double getUpperBand(Symbol symbol) {
        double mean = getMean(symbol);
        double std = getStdDev(symbol);
        return mean + entryZScore * std;
    }

    /**
     * Gets the lower Bollinger Band (mean - 2 * stdDev).
     */
    public double getLowerBand(Symbol symbol) {
        double mean = getMean(symbol);
        double std = getStdDev(symbol);
        return mean - entryZScore * std;
    }

    /**
     * Creates a builder for MeanReversionStrategy.
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

        public Builder lookbackPeriod(int period) {
            parameters.set("lookbackPeriod", period);
            return this;
        }

        public Builder entryZScore(double zScore) {
            parameters.set("entryZScore", zScore);
            return this;
        }

        public Builder exitZScore(double zScore) {
            parameters.set("exitZScore", zScore);
            return this;
        }

        public Builder maxPositionSize(long size) {
            parameters.set("maxPositionSize", size);
            return this;
        }

        public MeanReversionStrategy build() {
            if (symbols.isEmpty()) {
                throw new IllegalStateException("At least one symbol is required");
            }
            return new MeanReversionStrategy(symbols, parameters);
        }
    }
}
