package com.hft.algo.strategy;

import com.hft.algo.base.AbstractTradingStrategy;
import com.hft.algo.base.StrategyParameters;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;

import java.util.*;

/**
 * Bollinger Band / Keltner Channel squeeze strategy.
 *
 * Detects when Bollinger Bands contract inside Keltner Channels (a "squeeze"),
 * then trades the breakout direction using MACD histogram as the directional indicator.
 *
 * Squeeze condition: BB upper < KC upper AND BB lower > KC lower
 * Entry: When squeeze releases (BB expands outside KC), trade in MACD histogram direction.
 *
 * Parameters:
 * - bbPeriod: Bollinger Band SMA period (default: 20)
 * - bbStdDev: BB standard deviation multiplier (default: 2.5)
 * - kcPeriod: Keltner Channel EMA period (default: 20)
 * - kcAtrPeriod: KC ATR period (default: 14)
 * - kcMultiplier: KC ATR multiplier (default: 2.0)
 * - macdFast: MACD fast EMA period (default: 8)
 * - macdSlow: MACD slow EMA period (default: 17)
 * - macdSignal: MACD signal line period (default: 9)
 * - maxPositionSize: Maximum position per symbol (default: 1000)
 */
public class BollingerSqueezeStrategy extends AbstractTradingStrategy {

    private static final String NAME = "BollingerSqueeze";

    // BB tracking (SMA-based)
    private final Map<Symbol, LinkedList<Double>> priceHistories = new HashMap<>();
    private final Map<Symbol, Double> bbUpper = new HashMap<>();
    private final Map<Symbol, Double> bbLower = new HashMap<>();
    private final Map<Symbol, Double> bbMid = new HashMap<>();

    // KC tracking (EMA + ATR)
    private final Map<Symbol, Double> kcEma = new HashMap<>();
    private final Map<Symbol, Double> kcAtr = new HashMap<>();
    private final Map<Symbol, Double> prevCloseKc = new HashMap<>();
    private final Map<Symbol, Integer> atrSampleCount = new HashMap<>();

    // MACD tracking
    private final Map<Symbol, Double> macdFastEma = new HashMap<>();
    private final Map<Symbol, Double> macdSlowEma = new HashMap<>();
    private final Map<Symbol, Double> macdSignalEma = new HashMap<>();
    private final Map<Symbol, Double> macdHistogram = new HashMap<>();

    // Squeeze state
    private final Map<Symbol, Boolean> inSqueeze = new HashMap<>();
    private final Map<Symbol, Boolean> prevInSqueeze = new HashMap<>();

    // Parameters
    private int bbPeriod;
    private double bbStdDev;
    private int kcPeriod;
    private int kcAtrPeriod;
    private double kcMultiplier;
    private int macdFast;
    private int macdSlow;
    private int macdSignalPeriod;
    private long maxPositionSize;

    // EMA multipliers
    private double kcEmaMultiplier;
    private double macdFastMultiplier;
    private double macdSlowMultiplier;
    private double macdSignalMultiplier;

    public BollingerSqueezeStrategy(Set<Symbol> symbols, StrategyParameters parameters) {
        this(symbols, parameters, null);
    }

    public BollingerSqueezeStrategy(Set<Symbol> symbols, StrategyParameters parameters, String customName) {
        super(symbols, parameters, customName);
        loadParameters();

        for (Symbol symbol : symbols) {
            priceHistories.put(symbol, new LinkedList<>());
            inSqueeze.put(symbol, false);
            prevInSqueeze.put(symbol, false);
        }
    }

    public BollingerSqueezeStrategy(Set<Symbol> symbols) {
        this(symbols, defaultParameters());
    }

    private static StrategyParameters defaultParameters() {
        return new StrategyParameters()
                .set("bbPeriod", 20)
                .set("bbStdDev", 2.5)
                .set("kcPeriod", 20)
                .set("kcAtrPeriod", 14)
                .set("kcMultiplier", 2.0)
                .set("macdFast", 8)
                .set("macdSlow", 17)
                .set("macdSignal", 9)
                .set("maxPositionSize", 1000L);
    }

    private void loadParameters() {
        this.bbPeriod = parameters.getInt("bbPeriod", 20);
        this.bbStdDev = parameters.getDouble("bbStdDev", 2.5);
        this.kcPeriod = parameters.getInt("kcPeriod", 20);
        this.kcAtrPeriod = parameters.getInt("kcAtrPeriod", 14);
        this.kcMultiplier = parameters.getDouble("kcMultiplier", 2.0);
        this.macdFast = parameters.getInt("macdFast", 8);
        this.macdSlow = parameters.getInt("macdSlow", 17);
        this.macdSignalPeriod = parameters.getInt("macdSignal", 9);
        this.maxPositionSize = (long) (parameters.getDouble("maxPositionSize", 1000) * getQuantityScale());

        this.kcEmaMultiplier = 2.0 / (kcPeriod + 1);
        this.macdFastMultiplier = 2.0 / (macdFast + 1);
        this.macdSlowMultiplier = 2.0 / (macdSlow + 1);
        this.macdSignalMultiplier = 2.0 / (macdSignalPeriod + 1);
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

        // Update all indicators
        updateBollingerBands(symbol, close);
        updateKeltnerChannel(symbol, high, low, close);
        updateMacd(symbol, close);

        // Determine squeeze state
        boolean squeeze = isSqueeze(symbol);
        boolean wasSqueeze = prevInSqueeze.getOrDefault(symbol, false);
        prevInSqueeze.put(symbol, inSqueeze.getOrDefault(symbol, false));
        inSqueeze.put(symbol, squeeze);

        // Breakout: was in squeeze, now not
        if (wasSqueeze && !squeeze) {
            double histogram = macdHistogram.getOrDefault(symbol, 0.0);
            if (histogram > 0) {
                return Math.min(1.0, histogram / 100.0 + 0.5); // Bullish breakout
            } else if (histogram < 0) {
                return Math.max(-1.0, histogram / 100.0 - 0.5); // Bearish breakout
            }
        }

        // If currently in squeeze, flat signal (wait for breakout)
        if (squeeze) {
            return 0.0;
        }

        // Outside squeeze: attenuated MACD-based signal
        double histogram = macdHistogram.getOrDefault(symbol, 0.0);
        double normalizedHist = Math.max(-1.0, Math.min(1.0, histogram / 200.0));
        return normalizedHist * 0.3; // Weaker signal when not a breakout
    }

    private void updateBollingerBands(Symbol symbol, double close) {
        LinkedList<Double> history = priceHistories.get(symbol);
        history.addLast(close);
        while (history.size() > bbPeriod) {
            history.removeFirst();
        }

        if (history.size() < bbPeriod) {
            return;
        }

        double sum = 0;
        for (double p : history) {
            sum += p;
        }
        double sma = sum / history.size();

        double sumSqDiff = 0;
        for (double p : history) {
            double diff = p - sma;
            sumSqDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSqDiff / history.size());

        bbMid.put(symbol, sma);
        bbUpper.put(symbol, sma + bbStdDev * stdDev);
        bbLower.put(symbol, sma - bbStdDev * stdDev);
    }

    private void updateKeltnerChannel(Symbol symbol, double high, double low, double close) {
        // EMA of close
        if (!kcEma.containsKey(symbol)) {
            kcEma.put(symbol, close);
            kcAtr.put(symbol, high - low);
            atrSampleCount.put(symbol, 1);
            prevCloseKc.put(symbol, close);
            return;
        }

        // Update KC EMA
        double prevEma = kcEma.get(symbol);
        double newEma = (close - prevEma) * kcEmaMultiplier + prevEma;
        kcEma.put(symbol, newEma);

        // Update ATR (Wilder's smoothing)
        double prevC = prevCloseKc.get(symbol);
        double tr = Math.max(high - low,
                Math.max(Math.abs(high - prevC), Math.abs(low - prevC)));
        prevCloseKc.put(symbol, close);

        int count = atrSampleCount.get(symbol) + 1;
        atrSampleCount.put(symbol, count);

        double currentAtr = kcAtr.get(symbol);
        if (count <= kcAtrPeriod) {
            kcAtr.put(symbol, currentAtr + (tr - currentAtr) / count);
        } else {
            kcAtr.put(symbol, (currentAtr * (kcAtrPeriod - 1) + tr) / kcAtrPeriod);
        }
    }

    private void updateMacd(Symbol symbol, double close) {
        if (!macdFastEma.containsKey(symbol)) {
            macdFastEma.put(symbol, close);
            macdSlowEma.put(symbol, close);
            macdSignalEma.put(symbol, 0.0);
            macdHistogram.put(symbol, 0.0);
            return;
        }

        double prevFast = macdFastEma.get(symbol);
        double prevSlow = macdSlowEma.get(symbol);

        double newFast = (close - prevFast) * macdFastMultiplier + prevFast;
        double newSlow = (close - prevSlow) * macdSlowMultiplier + prevSlow;

        macdFastEma.put(symbol, newFast);
        macdSlowEma.put(symbol, newSlow);

        double macdLine = newFast - newSlow;
        double prevSignal = macdSignalEma.get(symbol);
        double newSignal = (macdLine - prevSignal) * macdSignalMultiplier + prevSignal;
        macdSignalEma.put(symbol, newSignal);

        macdHistogram.put(symbol, macdLine - newSignal);
    }

    private boolean isSqueeze(Symbol symbol) {
        Double bbUp = bbUpper.get(symbol);
        Double bbLow = bbLower.get(symbol);
        if (bbUp == null || bbLow == null) return false;

        Double kcE = kcEma.get(symbol);
        Double atr = kcAtr.get(symbol);
        if (kcE == null || atr == null) return false;

        double kcUp = kcE + kcMultiplier * atr;
        double kcLo = kcE - kcMultiplier * atr;

        return bbUp < kcUp && bbLow > kcLo;
    }

    @Override
    protected long calculateTargetPosition(Symbol symbol, double signal) {
        if (Math.abs(signal) < 0.01) {
            return 0;
        }
        long targetSize = (long) (maxPositionSize * Math.abs(signal));
        return signal > 0 ? targetSize : -targetSize;
    }

    public boolean isInSqueeze(Symbol symbol) {
        return inSqueeze.getOrDefault(symbol, false);
    }

    public double getMacdHistogram(Symbol symbol) {
        return macdHistogram.getOrDefault(symbol, 0.0);
    }

    public double getBbUpper(Symbol symbol) {
        return bbUpper.getOrDefault(symbol, 0.0);
    }

    public double getBbLower(Symbol symbol) {
        return bbLower.getOrDefault(symbol, 0.0);
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

        public Builder bbPeriod(int period) {
            parameters.set("bbPeriod", period);
            return this;
        }

        public Builder bbStdDev(double stdDev) {
            parameters.set("bbStdDev", stdDev);
            return this;
        }

        public Builder kcPeriod(int period) {
            parameters.set("kcPeriod", period);
            return this;
        }

        public Builder kcAtrPeriod(int period) {
            parameters.set("kcAtrPeriod", period);
            return this;
        }

        public Builder kcMultiplier(double multiplier) {
            parameters.set("kcMultiplier", multiplier);
            return this;
        }

        public Builder macdFast(int period) {
            parameters.set("macdFast", period);
            return this;
        }

        public Builder macdSlow(int period) {
            parameters.set("macdSlow", period);
            return this;
        }

        public Builder macdSignal(int period) {
            parameters.set("macdSignal", period);
            return this;
        }

        public Builder maxPositionSize(long size) {
            parameters.set("maxPositionSize", size);
            return this;
        }

        public BollingerSqueezeStrategy build() {
            if (symbols.isEmpty()) {
                throw new IllegalStateException("At least one symbol is required");
            }
            return new BollingerSqueezeStrategy(symbols, parameters);
        }
    }
}
