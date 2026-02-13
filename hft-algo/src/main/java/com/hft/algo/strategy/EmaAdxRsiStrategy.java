package com.hft.algo.strategy;

import com.hft.algo.base.AbstractTradingStrategy;
import com.hft.algo.base.StrategyParameters;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;

import java.util.*;

/**
 * Trend-following strategy combining EMA crossover with ADX and RSI filters.
 *
 * Entry logic:
 * - EMA 9/21 crossover determines direction (fast above slow = bullish)
 * - ADX must be above threshold (default 25) confirming a strong trend
 * - RSI must confirm direction (above rsiBullThreshold for buys, below rsiBearThreshold for sells)
 *
 * Parameters:
 * - fastEmaPeriod: Fast EMA period (default: 9)
 * - slowEmaPeriod: Slow EMA period (default: 21)
 * - adxPeriod: ADX smoothing period (default: 14)
 * - adxThreshold: Minimum ADX for entry (default: 25)
 * - rsiPeriod: RSI lookback period (default: 14)
 * - rsiBullThreshold: RSI must be above this for buys (default: 55)
 * - rsiBearThreshold: RSI must be below this for sells (default: 45)
 * - maxPositionSize: Maximum position per symbol (default: 1000)
 */
public class EmaAdxRsiStrategy extends AbstractTradingStrategy {

    private static final String NAME = "EmaAdxRsi";

    // EMA tracking
    private final Map<Symbol, Double> fastEma = new HashMap<>();
    private final Map<Symbol, Double> slowEma = new HashMap<>();

    // ADX tracking (Wilder's smoothing)
    private final Map<Symbol, Double> prevClose = new HashMap<>();
    private final Map<Symbol, Double> prevHigh = new HashMap<>();
    private final Map<Symbol, Double> prevLow = new HashMap<>();
    private final Map<Symbol, Double> smoothedPlusDm = new HashMap<>();
    private final Map<Symbol, Double> smoothedMinusDm = new HashMap<>();
    private final Map<Symbol, Double> smoothedTr = new HashMap<>();
    private final Map<Symbol, Double> adxValues = new HashMap<>();
    private final Map<Symbol, Double> prevAdx = new HashMap<>();
    private final Map<Symbol, Integer> adxSampleCount = new HashMap<>();

    // RSI tracking (Wilder's method)
    private final Map<Symbol, Double> avgGain = new HashMap<>();
    private final Map<Symbol, Double> avgLoss = new HashMap<>();
    private final Map<Symbol, Double> rsiValues = new HashMap<>();
    private final Map<Symbol, Integer> rsiSampleCount = new HashMap<>();
    private final Map<Symbol, Double> prevPrice = new HashMap<>();

    // Parameters
    private int fastEmaPeriod;
    private int slowEmaPeriod;
    private int adxPeriod;
    private double adxThreshold;
    private int rsiPeriod;
    private double rsiBullThreshold;
    private double rsiBearThreshold;
    private long maxPositionSize;

    // EMA multipliers
    private double fastMultiplier;
    private double slowMultiplier;

    public EmaAdxRsiStrategy(Set<Symbol> symbols, StrategyParameters parameters) {
        this(symbols, parameters, null);
    }

    public EmaAdxRsiStrategy(Set<Symbol> symbols, StrategyParameters parameters, String customName) {
        super(symbols, parameters, customName);
        loadParameters();
    }

    public EmaAdxRsiStrategy(Set<Symbol> symbols) {
        this(symbols, defaultParameters());
    }

    private static StrategyParameters defaultParameters() {
        return new StrategyParameters()
                .set("fastEmaPeriod", 9)
                .set("slowEmaPeriod", 21)
                .set("adxPeriod", 14)
                .set("adxThreshold", 25.0)
                .set("rsiPeriod", 14)
                .set("rsiBullThreshold", 55.0)
                .set("rsiBearThreshold", 45.0)
                .set("maxPositionSize", 1000L);
    }

    private void loadParameters() {
        this.fastEmaPeriod = parameters.getInt("fastEmaPeriod", 9);
        this.slowEmaPeriod = parameters.getInt("slowEmaPeriod", 21);
        this.adxPeriod = parameters.getInt("adxPeriod", 14);
        this.adxThreshold = parameters.getDouble("adxThreshold", 25.0);
        this.rsiPeriod = parameters.getInt("rsiPeriod", 14);
        this.rsiBullThreshold = parameters.getDouble("rsiBullThreshold", 55.0);
        this.rsiBearThreshold = parameters.getDouble("rsiBearThreshold", 45.0);
        this.maxPositionSize = parameters.getLong("maxPositionSize", 1000);

        this.fastMultiplier = 2.0 / (fastEmaPeriod + 1);
        this.slowMultiplier = 2.0 / (slowEmaPeriod + 1);
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
        double high = quote.getAskPrice();
        double low = quote.getBidPrice();
        double close = (high + low) / 2.0;

        // Update RSI
        updateRsi(symbol, close);

        // Update ADX
        updateAdx(symbol, high, low, close);

        // Update EMAs
        double signal = updateEmasAndGetSignal(symbol, close);

        // Store previous values for next iteration
        prevClose.put(symbol, close);
        prevHigh.put(symbol, high);
        prevLow.put(symbol, low);

        return signal;
    }

    private double updateEmasAndGetSignal(Symbol symbol, double close) {
        if (!fastEma.containsKey(symbol)) {
            fastEma.put(symbol, close);
            slowEma.put(symbol, close);
            return 0.0;
        }

        double prevFast = fastEma.get(symbol);
        double prevSlow = slowEma.get(symbol);

        double newFast = (close - prevFast) * fastMultiplier + prevFast;
        double newSlow = (close - prevSlow) * slowMultiplier + prevSlow;

        fastEma.put(symbol, newFast);
        slowEma.put(symbol, newSlow);

        if (newSlow == 0) return 0.0;

        // EMA crossover direction
        double emaCross = (newFast - newSlow) / newSlow;
        boolean bullish = newFast > newSlow;

        // Check ADX filter
        double adx = adxValues.getOrDefault(symbol, 0.0);
        if (adx < adxThreshold) {
            return 0.0; // No trend - no signal
        }

        // Check RSI confirmation
        double rsi = rsiValues.getOrDefault(symbol, 50.0);
        if (bullish && rsi < rsiBullThreshold) {
            return 0.0; // Bullish crossover but RSI doesn't confirm
        }
        if (!bullish && rsi > rsiBearThreshold) {
            return 0.0; // Bearish crossover but RSI doesn't confirm
        }

        // Normalize signal: scale by how far ADX exceeds threshold
        double adxStrength = Math.min(1.0, (adx - adxThreshold) / adxThreshold);
        double rawSignal = Math.signum(emaCross) * adxStrength;
        return Math.max(-1.0, Math.min(1.0, rawSignal));
    }

    private void updateRsi(Symbol symbol, double close) {
        if (!prevPrice.containsKey(symbol)) {
            prevPrice.put(symbol, close);
            rsiSampleCount.put(symbol, 0);
            avgGain.put(symbol, 0.0);
            avgLoss.put(symbol, 0.0);
            rsiValues.put(symbol, 50.0);
            return;
        }

        double change = close - prevPrice.get(symbol);
        prevPrice.put(symbol, close);
        double gain = Math.max(0, change);
        double loss = Math.max(0, -change);

        int count = rsiSampleCount.get(symbol) + 1;
        rsiSampleCount.put(symbol, count);

        if (count <= rsiPeriod) {
            // Initial averaging phase
            avgGain.put(symbol, avgGain.get(symbol) + gain / rsiPeriod);
            avgLoss.put(symbol, avgLoss.get(symbol) + loss / rsiPeriod);

            if (count == rsiPeriod) {
                double ag = avgGain.get(symbol);
                double al = avgLoss.get(symbol);
                if (al == 0) {
                    rsiValues.put(symbol, 100.0);
                } else {
                    rsiValues.put(symbol, 100.0 - 100.0 / (1.0 + ag / al));
                }
            }
        } else {
            // Wilder's smoothing
            double ag = (avgGain.get(symbol) * (rsiPeriod - 1) + gain) / rsiPeriod;
            double al = (avgLoss.get(symbol) * (rsiPeriod - 1) + loss) / rsiPeriod;
            avgGain.put(symbol, ag);
            avgLoss.put(symbol, al);

            if (al == 0) {
                rsiValues.put(symbol, 100.0);
            } else {
                rsiValues.put(symbol, 100.0 - 100.0 / (1.0 + ag / al));
            }
        }
    }

    private void updateAdx(Symbol symbol, double high, double low, double close) {
        if (!prevClose.containsKey(symbol)) {
            adxSampleCount.put(symbol, 0);
            smoothedPlusDm.put(symbol, 0.0);
            smoothedMinusDm.put(symbol, 0.0);
            smoothedTr.put(symbol, 0.0);
            adxValues.put(symbol, 0.0);
            prevAdx.put(symbol, 0.0);
            return;
        }

        double prevC = prevClose.get(symbol);
        double prevH = prevHigh.get(symbol);
        double prevL = prevLow.get(symbol);

        // True Range
        double tr = Math.max(high - low,
                Math.max(Math.abs(high - prevC), Math.abs(low - prevC)));

        // Directional Movement
        double upMove = high - prevH;
        double downMove = prevL - low;
        double plusDm = (upMove > downMove && upMove > 0) ? upMove : 0;
        double minusDm = (downMove > upMove && downMove > 0) ? downMove : 0;

        int count = adxSampleCount.get(symbol) + 1;
        adxSampleCount.put(symbol, count);

        if (count <= adxPeriod) {
            // Accumulation phase
            smoothedTr.put(symbol, smoothedTr.get(symbol) + tr);
            smoothedPlusDm.put(symbol, smoothedPlusDm.get(symbol) + plusDm);
            smoothedMinusDm.put(symbol, smoothedMinusDm.get(symbol) + minusDm);

            if (count == adxPeriod) {
                // First ADX calculation
                double sTr = smoothedTr.get(symbol);
                double sPdm = smoothedPlusDm.get(symbol);
                double sMdm = smoothedMinusDm.get(symbol);

                if (sTr > 0) {
                    double plusDi = 100.0 * sPdm / sTr;
                    double minusDi = 100.0 * sMdm / sTr;
                    double diSum = plusDi + minusDi;
                    double dx = diSum > 0 ? 100.0 * Math.abs(plusDi - minusDi) / diSum : 0;
                    adxValues.put(symbol, dx);
                    prevAdx.put(symbol, dx);
                }
            }
        } else {
            // Wilder's smoothing for TR, +DM, -DM
            double sTr = smoothedTr.get(symbol) - smoothedTr.get(symbol) / adxPeriod + tr;
            double sPdm = smoothedPlusDm.get(symbol) - smoothedPlusDm.get(symbol) / adxPeriod + plusDm;
            double sMdm = smoothedMinusDm.get(symbol) - smoothedMinusDm.get(symbol) / adxPeriod + minusDm;

            smoothedTr.put(symbol, sTr);
            smoothedPlusDm.put(symbol, sPdm);
            smoothedMinusDm.put(symbol, sMdm);

            if (sTr > 0) {
                double plusDi = 100.0 * sPdm / sTr;
                double minusDi = 100.0 * sMdm / sTr;
                double diSum = plusDi + minusDi;
                double dx = diSum > 0 ? 100.0 * Math.abs(plusDi - minusDi) / diSum : 0;

                // Smooth ADX
                double prevAdxVal = prevAdx.get(symbol);
                double adx = (prevAdxVal * (adxPeriod - 1) + dx) / adxPeriod;
                adxValues.put(symbol, adx);
                prevAdx.put(symbol, adx);
            }
        }
    }

    @Override
    protected long calculateTargetPosition(Symbol symbol, double signal) {
        if (Math.abs(signal) < 0.01) {
            return 0;
        }

        long targetSize = (long) (maxPositionSize * Math.abs(signal));
        return signal > 0 ? targetSize : -targetSize;
    }

    public double getFastEma(Symbol symbol) {
        return fastEma.getOrDefault(symbol, 0.0);
    }

    public double getSlowEma(Symbol symbol) {
        return slowEma.getOrDefault(symbol, 0.0);
    }

    public double getAdx(Symbol symbol) {
        return adxValues.getOrDefault(symbol, 0.0);
    }

    public double getRsi(Symbol symbol) {
        return rsiValues.getOrDefault(symbol, 50.0);
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

        public Builder fastEmaPeriod(int period) {
            parameters.set("fastEmaPeriod", period);
            return this;
        }

        public Builder slowEmaPeriod(int period) {
            parameters.set("slowEmaPeriod", period);
            return this;
        }

        public Builder adxPeriod(int period) {
            parameters.set("adxPeriod", period);
            return this;
        }

        public Builder adxThreshold(double threshold) {
            parameters.set("adxThreshold", threshold);
            return this;
        }

        public Builder rsiPeriod(int period) {
            parameters.set("rsiPeriod", period);
            return this;
        }

        public Builder rsiBullThreshold(double threshold) {
            parameters.set("rsiBullThreshold", threshold);
            return this;
        }

        public Builder rsiBearThreshold(double threshold) {
            parameters.set("rsiBearThreshold", threshold);
            return this;
        }

        public Builder maxPositionSize(long size) {
            parameters.set("maxPositionSize", size);
            return this;
        }

        public EmaAdxRsiStrategy build() {
            if (symbols.isEmpty()) {
                throw new IllegalStateException("At least one symbol is required");
            }
            return new EmaAdxRsiStrategy(symbols, parameters);
        }
    }
}
