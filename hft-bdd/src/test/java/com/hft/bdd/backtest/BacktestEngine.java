package com.hft.bdd.backtest;

import com.hft.algo.base.*;
import com.hft.algo.strategy.*;
import com.hft.core.model.*;

import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Backtest engine that replays historical candle data through trading strategies.
 *
 * The engine feeds candles as quotes to a strategy, reads the generated signal,
 * and manages a simulated dollar-denominated portfolio. The strategy's internal
 * position tracking is maintained via simulated fills so that stateful signal
 * logic (e.g., VWAP exit signals) works correctly.
 *
 * Supports two portfolio modes:
 * - Reinvest: compound returns across the full period
 * - Daily reset: start each day with the initial capital, accumulate daily P&Ls
 */
public class BacktestEngine {

    public static final double COMMISSION_RATE = 0.001; // 0.1% Binance taker fee
    public static final double MIN_TRADE_VALUE = 10.0;  // Minimum $10 notional per trade
    public static final int PRICE_SCALE = 100;          // 2 decimal places (cents)

    public BacktestResult run(BacktestConfig config, List<Candle> candles) {
        if (candles.isEmpty()) {
            return emptyResult(config);
        }

        Symbol symbol = config.symbol();
        TradingStrategy strategy = createStrategy(config.strategyType(), symbol);
        SimulatedContext ctx = new SimulatedContext();
        strategy.initialize(ctx);
        strategy.start();

        // External portfolio tracking (in dollars)
        double cash = config.initialCapital();
        double positionCoins = 0;
        double entryPrice = 0;

        // Performance metrics
        double peakValue = config.initialCapital();
        double maxDrawdown = 0;
        double totalCommission = 0;
        int totalTrades = 0;
        int winningTrades = 0;

        // Daily tracking
        List<BacktestResult.DailySnapshot> dailySnapshots = new ArrayList<>();
        List<BacktestResult.TradeRecord> tradeRecords = new ArrayList<>();
        int prevDayKey = -1;
        double dayStartValue = config.initialCapital();
        double cumulativeDailyPnl = 0;
        long dataPoints = 0;
        double lastClose = candles.get(0).close();

        for (Candle candle : candles) {
            Instant instant = Instant.ofEpochSecond(candle.time());
            LocalDateTime dt = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            int dayKey = dt.getYear() * 1000 + dt.getDayOfYear();

            // --- Day boundary ---
            if (prevDayKey != -1 && dayKey != prevDayKey) {
                double eodValue = cash + positionCoins * lastClose;
                double dailyReturn = dayStartValue > 0
                        ? ((eodValue - dayStartValue) / dayStartValue) * 100.0 : 0;
                dailySnapshots.add(new BacktestResult.DailySnapshot(
                        dt.toLocalDate().minusDays(1), eodValue, dailyReturn));

                if (!config.reinvestProfits()) {
                    // Close open position at start of new day
                    if (Math.abs(positionCoins) > 1e-10) {
                        double closePrice = candle.open();
                        double pnl = positionCoins > 0
                                ? positionCoins * (closePrice - entryPrice)
                                : Math.abs(positionCoins) * (entryPrice - closePrice);
                        double commission = Math.abs(positionCoins) * closePrice * COMMISSION_RATE;
                        cash += positionCoins * closePrice - commission;
                        totalCommission += commission;
                        totalTrades++;
                        if (pnl > commission) winningTrades++;

                        resetStrategyPosition(strategy, symbol, closePrice);
                        positionCoins = 0;
                        entryPrice = 0;
                    }
                    double dayPnl = cash - config.initialCapital();
                    cumulativeDailyPnl += dayPnl;
                    cash = config.initialCapital();
                }
                dayStartValue = cash + positionCoins * candle.open();
            }
            prevDayKey = dayKey;

            // --- Period filter ---
            TradingPeriod period = TradingPeriod.fromUtcTime(dt.toLocalTime());
            if (config.periodFilter() != null && period != config.periodFilter()) {
                lastClose = candle.close();
                continue;
            }
            double multiplier = period.getPositionMultiplier();

            // --- Create quote from candle ---
            long bidPrice = (long) (candle.low() * PRICE_SCALE);
            long askPrice = (long) (candle.high() * PRICE_SCALE);
            if (bidPrice == askPrice) askPrice = bidPrice + 1;

            Quote quote = new Quote();
            quote.setSymbol(symbol);
            quote.setBidPrice(bidPrice);
            quote.setAskPrice(askPrice);
            quote.setBidSize(candle.volume());
            quote.setAskSize(candle.volume());
            quote.setTimestamp(candle.time() * 1_000_000_000L);
            quote.setPriceScale(PRICE_SCALE);

            ctx.setCurrentTime(candle.time() * 1_000_000_000L, candle.time() * 1000);
            ctx.setQuote(symbol, quote);

            // --- Feed to strategy ---
            strategy.onQuote(quote);
            dataPoints++;

            // --- Read signal and apply period multiplier ---
            double rawSignal = strategy.getSignal(symbol);
            double signal = Math.max(-1.0, Math.min(1.0, rawSignal * multiplier));

            // --- Calculate target position ---
            double totalValue = cash + positionCoins * candle.close();
            if (totalValue <= 0) break; // Portfolio wiped out

            double targetPositionValue = signal * totalValue;
            double targetCoins = targetPositionValue / candle.close();
            double deltaCoins = targetCoins - positionCoins;
            double deltaValue = Math.abs(deltaCoins) * candle.close();

            // --- Execute trade if significant ---
            if (deltaValue > MIN_TRADE_VALUE) {
                double commission = deltaValue * COMMISSION_RATE;

                // Track win/loss on position close
                boolean closingLong = positionCoins > 1e-10 && deltaCoins < 0;
                boolean closingShort = positionCoins < -1e-10 && deltaCoins > 0;

                if ((closingLong || closingShort) && entryPrice > 0) {
                    double closingCoins = Math.min(Math.abs(positionCoins), Math.abs(deltaCoins));
                    double pnl = closingLong
                            ? closingCoins * (candle.close() - entryPrice)
                            : closingCoins * (entryPrice - candle.close());
                    if (pnl > commission) winningTrades++;
                }

                // Execute
                cash -= deltaCoins * candle.close();
                cash -= commission;
                positionCoins += deltaCoins;
                totalCommission += commission;
                totalTrades++;

                // Update entry price
                if (Math.abs(positionCoins) > 1e-10) {
                    double prevAbs = Math.abs(positionCoins - deltaCoins);
                    if (prevAbs < 1e-10) {
                        // Fresh entry
                        entryPrice = candle.close();
                    }
                    // Otherwise keep existing entry (simplified — no weighted avg)
                } else {
                    entryPrice = 0;
                }

                tradeRecords.add(new BacktestResult.TradeRecord(
                        dt, deltaCoins > 0 ? "BUY" : "SELL",
                        candle.close(), Math.abs(deltaCoins),
                        positionCoins * candle.close(),
                        cash + positionCoins * candle.close()));
            }

            // --- Drawdown ---
            double currentValue = cash + positionCoins * candle.close();
            if (currentValue > peakValue) peakValue = currentValue;
            double dd = peakValue > 0 ? (peakValue - currentValue) / peakValue : 0;
            if (dd > maxDrawdown) maxDrawdown = dd;

            lastClose = candle.close();
        }

        // --- Final day snapshot ---
        if (prevDayKey != -1) {
            double eodValue = cash + positionCoins * lastClose;
            double dailyReturn = dayStartValue > 0
                    ? ((eodValue - dayStartValue) / dayStartValue) * 100.0 : 0;
            dailySnapshots.add(new BacktestResult.DailySnapshot(
                    LocalDate.now(), eodValue, dailyReturn));
        }

        // --- Close final position ---
        if (Math.abs(positionCoins) > 1e-10) {
            double commission = Math.abs(positionCoins) * lastClose * COMMISSION_RATE;
            double pnl = positionCoins > 0
                    ? positionCoins * (lastClose - entryPrice)
                    : Math.abs(positionCoins) * (entryPrice - lastClose);
            cash += positionCoins * lastClose - commission;
            totalCommission += commission;
            totalTrades++;
            if (pnl > commission) winningTrades++;
            positionCoins = 0;
        }

        // --- Compute final metrics ---
        double finalValue;
        if (config.reinvestProfits()) {
            finalValue = cash;
        } else {
            double lastDayPnl = cash - config.initialCapital();
            cumulativeDailyPnl += lastDayPnl;
            finalValue = config.initialCapital() + cumulativeDailyPnl;
        }

        double totalReturn = ((finalValue - config.initialCapital()) / config.initialCapital()) * 100.0;
        double sharpe = calculateSharpeRatio(dailySnapshots);
        double profitFactor = calculateProfitFactor(dailySnapshots, config.initialCapital());

        return new BacktestResult(
                config, finalValue, totalReturn, maxDrawdown * 100.0,
                totalTrades, winningTrades, totalCommission,
                sharpe, profitFactor, dailySnapshots, tradeRecords, dataPoints);
    }

    // --- Strategy factory ---

    private TradingStrategy createStrategy(String type, Symbol symbol) {
        return switch (type.toLowerCase()) {
            case "ema_adx_rsi" -> EmaAdxRsiStrategy.builder()
                    .addSymbol(symbol)
                    .fastEmaPeriod(9).slowEmaPeriod(21)
                    .adxPeriod(14).adxThreshold(25.0)
                    .rsiPeriod(14)
                    .rsiBullThreshold(55.0).rsiBearThreshold(45.0)
                    .maxPositionSize(100)
                    .build();
            case "bollinger_squeeze" -> BollingerSqueezeStrategy.builder()
                    .addSymbol(symbol)
                    .bbPeriod(20).bbStdDev(2.5)
                    .kcPeriod(20).kcAtrPeriod(14).kcMultiplier(2.0)
                    .macdFast(8).macdSlow(17).macdSignal(9)
                    .maxPositionSize(100)
                    .build();
            case "vwap_mean_reversion" -> VwapMeanReversionStrategy.builder()
                    .addSymbol(symbol)
                    .upperSigma(2.3).lowerSigma(2.3)
                    .exitSigma(0.5)
                    .maxHoldMinutes(240)
                    .volumeFilterMultiplier(0.0) // Disable volume filter for backtest (candle-level data)
                    .maxPositionSize(100)
                    .build();
            default -> throw new IllegalArgumentException("Unknown strategy type: " + type);
        };
    }

    private void resetStrategyPosition(TradingStrategy strategy, Symbol symbol, double price) {
        long pos = strategy.getCurrentPosition(symbol);
        if (pos != 0) {
            Trade fill = new Trade();
            fill.setSymbol(symbol);
            fill.setSide(pos > 0 ? OrderSide.SELL : OrderSide.BUY);
            fill.setQuantity(Math.abs(pos));
            fill.setPrice((long) (price * PRICE_SCALE));
            strategy.onFill(fill);
        }
    }

    private double calculateSharpeRatio(List<BacktestResult.DailySnapshot> snapshots) {
        if (snapshots.size() < 2) return 0;
        double[] returns = snapshots.stream()
                .mapToDouble(BacktestResult.DailySnapshot::dailyReturnPercent)
                .toArray();
        double mean = Arrays.stream(returns).average().orElse(0);
        double variance = Arrays.stream(returns)
                .map(r -> (r - mean) * (r - mean))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        if (stdDev < 1e-6) return 0;
        return (mean / stdDev) * Math.sqrt(365); // Annualized (crypto trades 365 days)
    }

    private double calculateProfitFactor(List<BacktestResult.DailySnapshot> snapshots, double initialCapital) {
        double grossProfit = 0;
        double grossLoss = 0;
        for (var snap : snapshots) {
            double ret = snap.dailyReturnPercent();
            if (ret > 0) grossProfit += ret;
            else grossLoss += Math.abs(ret);
        }
        if (grossLoss < 1e-6) return grossProfit > 0 ? 99.99 : 0;
        return grossProfit / grossLoss;
    }

    private BacktestResult emptyResult(BacktestConfig config) {
        return new BacktestResult(
                config, config.initialCapital(), 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(), 0);
    }

    // --- Simulated AlgorithmContext ---

    static class SimulatedContext implements AlgorithmContext {
        private final Map<Symbol, Quote> quotes = new HashMap<>();
        private Consumer<Trade> fillCallback;
        private long currentTimeNanos;
        private long currentTimeMillis;

        void setCurrentTime(long nanos, long millis) {
            this.currentTimeNanos = nanos;
            this.currentTimeMillis = millis;
        }

        void setQuote(Symbol symbol, Quote quote) {
            quotes.put(symbol, quote);
        }

        @Override
        public Quote getQuote(Symbol symbol) {
            return quotes.get(symbol);
        }

        @Override
        public long getCurrentTimeNanos() {
            return currentTimeNanos;
        }

        @Override
        public long getCurrentTimeMillis() {
            return currentTimeMillis;
        }

        @Override
        public void submitOrder(OrderRequest request) {
            // Simulate immediate fill for strategy internal state
            Trade fill = new Trade();
            fill.setSymbol(request.getSymbol());
            fill.setSide(request.getSide());
            fill.setQuantity(request.getQuantity());
            fill.setPrice(request.getPrice());
            fill.setExecutedAt(currentTimeNanos);

            if (fillCallback != null) {
                fillCallback.accept(fill);
            }
        }

        @Override
        public void cancelOrder(long clientOrderId) {
            // No-op in backtest
        }

        @Override
        public void onFill(Consumer<Trade> callback) {
            this.fillCallback = callback;
        }

        @Override
        public long[] getHistoricalVolume(Symbol symbol, int buckets) {
            return new long[buckets];
        }

        @Override
        public void logInfo(String message) {
            // Silent in backtest
        }

        @Override
        public void logError(String message, Throwable error) {
            // Silent in backtest
        }
    }
}
