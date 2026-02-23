package com.hft.algo.strategy;

import com.hft.algo.base.AbstractTradingStrategy;
import com.hft.algo.base.StrategyParameters;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;

import java.util.*;

/**
 * VWAP Mean Reversion strategy.
 *
 * Trades mean reversion around the session VWAP (Volume-Weighted Average Price).
 * Uses sigma deviation bands to identify entry/exit points.
 *
 * Entry:
 * - Buy when price < VWAP - lowerSigma * sigma
 * - Sell when price > VWAP + upperSigma * sigma
 *
 * Exit:
 * - Close when |price - VWAP| < exitSigma * sigma (price returns to VWAP)
 *
 * Additional filters:
 * - Volume filter: only trade when current volume > volumeFilterMultiplier * average volume
 * - Max hold time: close after maxHoldMinutes
 *
 * Parameters:
 * - upperSigma: Upper band sigma multiplier (default: 2.3)
 * - lowerSigma: Lower band sigma multiplier (default: 2.3)
 * - exitSigma: Exit threshold sigma (default: 0.5)
 * - maxHoldMinutes: Maximum hold time in minutes (default: 240)
 * - volumeFilterMultiplier: Volume filter multiplier (default: 2.0)
 * - maxPositionSize: Maximum position per symbol (default: 1000)
 */
public class VwapMeanReversionStrategy extends AbstractTradingStrategy {

    private static final String NAME = "VwapMeanReversion";

    // VWAP tracking (cumulative)
    private final Map<Symbol, Double> cumulativePriceVolume = new HashMap<>();
    private final Map<Symbol, Double> cumulativeVolume = new HashMap<>();
    private final Map<Symbol, Double> vwapValues = new HashMap<>();

    // VWAP sigma (standard deviation from VWAP)
    private final Map<Symbol, Double> cumulativePriceSquaredVolume = new HashMap<>();
    private final Map<Symbol, Double> vwapSigma = new HashMap<>();

    // Volume tracking
    private final Map<Symbol, LinkedList<Double>> volumeHistory = new HashMap<>();
    private final Map<Symbol, Double> avgVolume = new HashMap<>();

    // Trade entry tracking
    private final Map<Symbol, Long> entryTimeNanos = new HashMap<>();
    private final Map<Symbol, Integer> sampleCount = new HashMap<>();

    // Parameters
    private double upperSigma;
    private double lowerSigma;
    private double exitSigma;
    private long maxHoldMinutes;
    private double volumeFilterMultiplier;
    private long maxPositionSize;

    public VwapMeanReversionStrategy(Set<Symbol> symbols, StrategyParameters parameters) {
        this(symbols, parameters, null);
    }

    public VwapMeanReversionStrategy(Set<Symbol> symbols, StrategyParameters parameters, String customName) {
        super(symbols, parameters, customName);
        loadParameters();

        for (Symbol symbol : symbols) {
            cumulativePriceVolume.put(symbol, 0.0);
            cumulativeVolume.put(symbol, 0.0);
            cumulativePriceSquaredVolume.put(symbol, 0.0);
            vwapValues.put(symbol, 0.0);
            vwapSigma.put(symbol, 0.0);
            volumeHistory.put(symbol, new LinkedList<>());
            avgVolume.put(symbol, 0.0);
            sampleCount.put(symbol, 0);
        }
    }

    public VwapMeanReversionStrategy(Set<Symbol> symbols) {
        this(symbols, defaultParameters());
    }

    private static StrategyParameters defaultParameters() {
        return new StrategyParameters()
                .set("upperSigma", 2.3)
                .set("lowerSigma", 2.3)
                .set("exitSigma", 0.5)
                .set("maxHoldMinutes", 240)
                .set("volumeFilterMultiplier", 2.0)
                .set("maxPositionSize", 1000L);
    }

    private void loadParameters() {
        this.upperSigma = parameters.getDouble("upperSigma", 2.3);
        this.lowerSigma = parameters.getDouble("lowerSigma", 2.3);
        this.exitSigma = parameters.getDouble("exitSigma", 0.5);
        this.maxHoldMinutes = parameters.getLong("maxHoldMinutes", 240);
        this.volumeFilterMultiplier = parameters.getDouble("volumeFilterMultiplier", 2.0);
        this.maxPositionSize = (long) (parameters.getDouble("maxPositionSize", 1000) * getQuantityScale());
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
        double price = (quote.getBidPrice() + quote.getAskPrice()) / 2.0;
        double volume = (quote.getBidSize() + quote.getAskSize()) / 2.0;
        if (volume <= 0) {
            volume = 1.0; // Default to 1 if no volume info
        }

        // Update VWAP
        updateVwap(symbol, price, volume);

        // Update volume tracking
        updateVolumeStats(symbol, volume);

        double vwap = vwapValues.get(symbol);
        double sigma = vwapSigma.get(symbol);

        // Need some history before generating signals
        int count = sampleCount.get(symbol);
        if (count < 10 || sigma < 0.0001) {
            return 0.0;
        }

        double deviation = (price - vwap) / sigma;
        long currentPos = getCurrentPosition(symbol);

        // Exit logic: close when price returns to VWAP
        if (currentPos != 0) {
            if (Math.abs(deviation) < exitSigma) {
                return 0.0; // Signal to flatten
            }

            // Check max hold time
            Long entryTime = entryTimeNanos.get(symbol);
            if (entryTime != null && context != null) {
                long holdNanos = context.getCurrentTimeNanos() - entryTime;
                long maxHoldNanos = maxHoldMinutes * 60L * 1_000_000_000L;
                if (holdNanos > maxHoldNanos) {
                    return 0.0; // Force exit on timeout
                }
            }

            // Hold current position direction
            return currentPos > 0 ? 0.5 : -0.5;
        }

        // Entry logic: volume filter
        double avgVol = avgVolume.getOrDefault(symbol, 0.0);
        if (avgVol > 0 && volume < avgVol * volumeFilterMultiplier) {
            return 0.0; // Volume too low
        }

        // Entry signals
        if (deviation < -lowerSigma) {
            // Price below VWAP - lowerSigma*sigma -> buy
            double strength = Math.min(1.0, Math.abs(deviation) / (lowerSigma * 2));
            entryTimeNanos.put(symbol, context != null ? context.getCurrentTimeNanos() : System.nanoTime());
            return strength;
        } else if (deviation > upperSigma) {
            // Price above VWAP + upperSigma*sigma -> sell
            double strength = Math.min(1.0, Math.abs(deviation) / (upperSigma * 2));
            entryTimeNanos.put(symbol, context != null ? context.getCurrentTimeNanos() : System.nanoTime());
            return -strength;
        }

        return 0.0;
    }

    private void updateVwap(Symbol symbol, double price, double volume) {
        int count = sampleCount.get(symbol) + 1;
        sampleCount.put(symbol, count);

        double cumPV = cumulativePriceVolume.get(symbol) + price * volume;
        double cumV = cumulativeVolume.get(symbol) + volume;
        double cumPSV = cumulativePriceSquaredVolume.get(symbol) + price * price * volume;

        cumulativePriceVolume.put(symbol, cumPV);
        cumulativeVolume.put(symbol, cumV);
        cumulativePriceSquaredVolume.put(symbol, cumPSV);

        if (cumV > 0) {
            double vwap = cumPV / cumV;
            vwapValues.put(symbol, vwap);

            // VWAP variance = E[P^2] - VWAP^2
            double variance = (cumPSV / cumV) - vwap * vwap;
            vwapSigma.put(symbol, variance > 0 ? Math.sqrt(variance) : 0.0);
        }
    }

    private void updateVolumeStats(Symbol symbol, double volume) {
        LinkedList<Double> history = volumeHistory.get(symbol);
        history.addLast(volume);
        while (history.size() > 20) {
            history.removeFirst();
        }

        double sum = 0;
        for (double v : history) {
            sum += v;
        }
        avgVolume.put(symbol, history.isEmpty() ? 0.0 : sum / history.size());
    }

    @Override
    protected long calculateTargetPosition(Symbol symbol, double signal) {
        if (Math.abs(signal) < 0.01) {
            return 0;
        }
        long targetSize = (long) (maxPositionSize * Math.abs(signal));
        return signal > 0 ? targetSize : -targetSize;
    }

    public double getVwap(Symbol symbol) {
        return vwapValues.getOrDefault(symbol, 0.0);
    }

    public double getVwapSigma(Symbol symbol) {
        return vwapSigma.getOrDefault(symbol, 0.0);
    }

    public double getUpperBand(Symbol symbol) {
        return getVwap(symbol) + upperSigma * getVwapSigma(symbol);
    }

    public double getLowerBand(Symbol symbol) {
        return getVwap(symbol) - lowerSigma * getVwapSigma(symbol);
    }

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

        public Builder upperSigma(double sigma) {
            parameters.set("upperSigma", sigma);
            return this;
        }

        public Builder lowerSigma(double sigma) {
            parameters.set("lowerSigma", sigma);
            return this;
        }

        public Builder exitSigma(double sigma) {
            parameters.set("exitSigma", sigma);
            return this;
        }

        public Builder maxHoldMinutes(long minutes) {
            parameters.set("maxHoldMinutes", minutes);
            return this;
        }

        public Builder volumeFilterMultiplier(double multiplier) {
            parameters.set("volumeFilterMultiplier", multiplier);
            return this;
        }

        public Builder maxPositionSize(long size) {
            parameters.set("maxPositionSize", size);
            return this;
        }

        public VwapMeanReversionStrategy build() {
            if (symbols.isEmpty()) {
                throw new IllegalStateException("At least one symbol is required");
            }
            return new VwapMeanReversionStrategy(symbols, parameters);
        }
    }
}
