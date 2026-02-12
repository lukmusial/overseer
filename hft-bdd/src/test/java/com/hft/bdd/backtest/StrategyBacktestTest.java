package com.hft.bdd.backtest;

import com.hft.core.model.Exchange;
import com.hft.core.model.Symbol;
import com.hft.core.model.TradingPeriod;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reusable backtest test that evaluates trading strategies against historical Binance data.
 *
 * Usage:
 *   ./gradlew :hft-bdd:backtest
 *
 * This test is excluded from normal test runs (@Tag("backtest")).
 * It fetches real 1-hour kline data from Binance public API for the last 3 months,
 * then runs each strategy through the BacktestEngine with $1,000 initial capital.
 *
 * Parameters tested:
 * - Strategies: ema_adx_rsi, bollinger_squeeze, vwap_mean_reversion
 * - Symbols: BTCUSDT, ETHUSDT
 * - Periods: ALL (no filter), plus each strategy's recommended periods
 * - Modes: compound (reinvest profits), daily_reset (same $1,000 each day)
 */
@Tag("backtest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StrategyBacktestTest {

    private static final double INITIAL_CAPITAL = 1000.0;
    private static final int MONTHS_OF_HISTORY = 3;

    private static Map<String, List<Candle>> candleCache = new LinkedHashMap<>();
    private static final BacktestEngine engine = new BacktestEngine();
    private static final List<BacktestResult> allResults = Collections.synchronizedList(new ArrayList<>());

    // --- Data fetching ---

    @BeforeAll
    static void fetchHistoricalData() {
        long endMs = System.currentTimeMillis();
        long startMs = endMs - (long) MONTHS_OF_HISTORY * 30 * 24 * 3_600_000L;

        HistoricalDataFetcher fetcher = new HistoricalDataFetcher();
        String[] symbols = {"BTCUSDT", "ETHUSDT"};

        for (String sym : symbols) {
            try {
                System.out.printf("Fetching %d months of 1h klines for %s...%n", MONTHS_OF_HISTORY, sym);
                List<Candle> candles = fetcher.fetchKlines(sym, "1h", startMs, endMs);
                candleCache.put(sym, candles);
                System.out.printf("  -> %d candles fetched (%.1f days)%n",
                        candles.size(), candles.size() / 24.0);

                if (!candles.isEmpty()) {
                    Candle first = candles.get(0);
                    Candle last = candles.get(candles.size() - 1);
                    System.out.printf("  -> Date range: %s to %s%n",
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(first.time()), ZoneOffset.UTC).toLocalDate(),
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(last.time()), ZoneOffset.UTC).toLocalDate());
                    System.out.printf("  -> Price range: $%.2f - $%.2f%n",
                            candles.stream().mapToDouble(Candle::low).min().orElse(0),
                            candles.stream().mapToDouble(Candle::high).max().orElse(0));
                }
            } catch (IOException e) {
                System.err.println("Failed to fetch data for " + sym + ": " + e.getMessage());
                candleCache.put(sym, List.of());
            }
        }
    }

    // --- Parameterized backtest ---

    @ParameterizedTest(name = "{0} | {1} | {2} | reinvest={3}")
    @MethodSource("backtestParameters")
    @Order(1)
    void runBacktest(String strategyType, String symbolTicker, String periodName, boolean reinvest) {
        List<Candle> candles = candleCache.get(symbolTicker);
        assumeTrue(candles != null && !candles.isEmpty(),
                "No historical data available for " + symbolTicker);

        Symbol symbol = new Symbol(symbolTicker, Exchange.BINANCE);
        TradingPeriod period = "ALL".equals(periodName) ? null : TradingPeriod.valueOf(periodName);

        BacktestConfig config = new BacktestConfig(symbol, strategyType, period, INITIAL_CAPITAL, reinvest);
        BacktestResult result = engine.run(config, candles);

        allResults.add(result);

        // Print result
        System.out.println(result.summary());

        // Basic assertions: engine ran and produced output
        assertTrue(result.dataPointsProcessed() > 0, "Should process data points");
        assertTrue(result.finalPortfolioValue() > 0, "Portfolio should not go negative");
    }

    static Stream<Arguments> backtestParameters() {
        List<Arguments> args = new ArrayList<>();

        String[] strategies = {"ema_adx_rsi", "bollinger_squeeze", "vwap_mean_reversion"};
        String[] symbols = {"BTCUSDT", "ETHUSDT"};
        boolean[] reinvestModes = {true, false};

        for (String strategy : strategies) {
            for (String symbol : symbols) {
                for (boolean reinvest : reinvestModes) {
                    // All periods (no filter)
                    args.add(Arguments.of(strategy, symbol, "ALL", reinvest));

                    // Recommended periods for this strategy
                    for (TradingPeriod period : TradingPeriod.values()) {
                        if (period.getRecommendedStrategies().contains(strategy)) {
                            args.add(Arguments.of(strategy, symbol, period.name(), reinvest));
                        }
                    }
                }
            }
        }

        return args.stream();
    }

    // --- Report generation ---

    @Test
    @Order(2)
    void generateReport() throws IOException {
        assumeTrue(!allResults.isEmpty(), "No backtest results to report");

        String report = buildReport();
        System.out.println("\n" + "=".repeat(120));
        System.out.println("BACKTEST REPORT SUMMARY");
        System.out.println("=".repeat(120));
        System.out.println(report);

        // Write to local-docs
        Path reportDir = Path.of("local-docs");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("BACKTEST_REPORT.md");
        Files.writeString(reportFile, report);
        System.out.println("\nReport written to: " + reportFile.toAbsolutePath());
    }

    private String buildReport() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        out.println("# Strategy Backtest Report");
        out.println();
        out.printf("**Generated:** %s UTC%n", LocalDateTime.now(ZoneOffset.UTC));
        out.printf("**Period:** Last %d months of 1-hour kline data%n", MONTHS_OF_HISTORY);
        out.printf("**Initial Capital:** $%,.0f%n", INITIAL_CAPITAL);
        out.printf("**Commission Model:** %.1f%% per trade (Binance taker fee)%n", BacktestEngine.COMMISSION_RATE * 100);
        out.println();

        // Data summary
        out.println("## Data Summary");
        out.println();
        for (var entry : candleCache.entrySet()) {
            List<Candle> candles = entry.getValue();
            if (!candles.isEmpty()) {
                Candle first = candles.get(0);
                Candle last = candles.get(candles.size() - 1);
                out.printf("- **%s**: %,d candles, %s to %s, $%.2f - $%.2f%n",
                        entry.getKey(), candles.size(),
                        LocalDateTime.ofInstant(Instant.ofEpochSecond(first.time()), ZoneOffset.UTC).toLocalDate(),
                        LocalDateTime.ofInstant(Instant.ofEpochSecond(last.time()), ZoneOffset.UTC).toLocalDate(),
                        candles.stream().mapToDouble(Candle::low).min().orElse(0),
                        candles.stream().mapToDouble(Candle::high).max().orElse(0));
            }
        }
        out.println();

        // Results by strategy
        String[] strategies = {"ema_adx_rsi", "bollinger_squeeze", "vwap_mean_reversion"};
        String[] strategyNames = {"EMA + ADX + RSI", "Bollinger Squeeze", "VWAP Mean Reversion"};

        for (int s = 0; s < strategies.length; s++) {
            String stratType = strategies[s];
            String stratName = strategyNames[s];

            out.printf("## %s (`%s`)%n%n", stratName, stratType);

            // Compound mode results
            out.println("### Compound Returns (Reinvest Profits)");
            out.println();
            out.println("| Symbol | Period | Final Value | Return | Max DD | Trades | Win Rate | Sharpe | PF |");
            out.println("|--------|--------|-------------|--------|--------|--------|----------|--------|----|");

            allResults.stream()
                    .filter(r -> r.config().strategyType().equals(stratType) && r.config().reinvestProfits())
                    .sorted(Comparator.comparing((BacktestResult r) -> r.config().symbol().getTicker())
                            .thenComparing(r -> r.config().periodFilter() == null ? "" : r.config().periodFilter().name()))
                    .forEach(r -> out.printf("| %s | %s | $%,.2f | %+.2f%% | %.2f%% | %d | %.1f%% | %.2f | %.2f |%n",
                            r.config().symbol().getTicker(),
                            r.config().periodFilter() != null ? r.config().periodFilter().name() : "ALL",
                            r.finalPortfolioValue(), r.totalReturnPercent(), r.maxDrawdownPercent(),
                            r.totalTrades(), r.winRatePercent(), r.sharpeRatio(), r.profitFactor()));

            out.println();

            // Daily reset mode results
            out.println("### Daily Reset (Same $1,000 Each Day)");
            out.println();
            out.println("| Symbol | Period | Final Value | Return | Max DD | Trades | Win Rate | Sharpe | PF |");
            out.println("|--------|--------|-------------|--------|--------|--------|----------|--------|----|");

            allResults.stream()
                    .filter(r -> r.config().strategyType().equals(stratType) && !r.config().reinvestProfits())
                    .sorted(Comparator.comparing((BacktestResult r) -> r.config().symbol().getTicker())
                            .thenComparing(r -> r.config().periodFilter() == null ? "" : r.config().periodFilter().name()))
                    .forEach(r -> out.printf("| %s | %s | $%,.2f | %+.2f%% | %.2f%% | %d | %.1f%% | %.2f | %.2f |%n",
                            r.config().symbol().getTicker(),
                            r.config().periodFilter() != null ? r.config().periodFilter().name() : "ALL",
                            r.finalPortfolioValue(), r.totalReturnPercent(), r.maxDrawdownPercent(),
                            r.totalTrades(), r.winRatePercent(), r.sharpeRatio(), r.profitFactor()));

            out.println();
        }

        // Trading period analysis
        out.println("## Trading Period Analysis");
        out.println();
        out.println("Performance by trading period across all strategies and symbols:");
        out.println();
        out.println("| Period | Hours (UTC) | Multiplier | Avg Return | Best Strategy |");
        out.println("|--------|-------------|------------|------------|---------------|");

        for (TradingPeriod period : TradingPeriod.values()) {
            if (period == TradingPeriod.OFF_HOURS) continue;

            OptionalDouble avgReturn = allResults.stream()
                    .filter(r -> r.config().periodFilter() == period && r.config().reinvestProfits())
                    .mapToDouble(BacktestResult::totalReturnPercent)
                    .average();

            String bestStrategy = allResults.stream()
                    .filter(r -> r.config().periodFilter() == period && r.config().reinvestProfits())
                    .max(Comparator.comparingDouble(BacktestResult::totalReturnPercent))
                    .map(r -> r.config().strategyType())
                    .orElse("N/A");

            out.printf("| %s | %s-%s | %.2f | %+.2f%% | %s |%n",
                    period.name(), period.getStartTime(), period.getEndTime(),
                    period.getPositionMultiplier(),
                    avgReturn.orElse(0),
                    bestStrategy);
        }
        out.println();

        // Key findings
        out.println("## Key Findings");
        out.println();

        // Best overall strategy (compound, ALL periods)
        allResults.stream()
                .filter(r -> r.config().periodFilter() == null && r.config().reinvestProfits())
                .max(Comparator.comparingDouble(BacktestResult::totalReturnPercent))
                .ifPresent(best -> out.printf("- **Best overall (compound):** %s on %s with %+.2f%% return ($%,.2f final)%n",
                        best.config().strategyType(), best.config().symbol().getTicker(),
                        best.totalReturnPercent(), best.finalPortfolioValue()));

        // Best overall (daily reset)
        allResults.stream()
                .filter(r -> r.config().periodFilter() == null && !r.config().reinvestProfits())
                .max(Comparator.comparingDouble(BacktestResult::totalReturnPercent))
                .ifPresent(best -> out.printf("- **Best overall (daily reset):** %s on %s with %+.2f%% return ($%,.2f final)%n",
                        best.config().strategyType(), best.config().symbol().getTicker(),
                        best.totalReturnPercent(), best.finalPortfolioValue()));

        // Lowest drawdown
        allResults.stream()
                .filter(r -> r.config().periodFilter() == null && r.config().reinvestProfits() && r.totalTrades() > 0)
                .min(Comparator.comparingDouble(BacktestResult::maxDrawdownPercent))
                .ifPresent(best -> out.printf("- **Lowest drawdown:** %s on %s with %.2f%% max drawdown%n",
                        best.config().strategyType(), best.config().symbol().getTicker(),
                        best.maxDrawdownPercent()));

        // Highest Sharpe
        allResults.stream()
                .filter(r -> r.config().periodFilter() == null && r.config().reinvestProfits())
                .max(Comparator.comparingDouble(BacktestResult::sharpeRatio))
                .ifPresent(best -> out.printf("- **Best risk-adjusted (Sharpe):** %s on %s with %.2f Sharpe ratio%n",
                        best.config().strategyType(), best.config().symbol().getTicker(),
                        best.sharpeRatio()));

        out.println();

        // Methodology
        out.println("## Methodology");
        out.println();
        out.println("- **Data source:** Binance public REST API (`/api/v3/klines`), 1-hour interval");
        out.println("- **Signal-based allocation:** Strategy signal (-1 to +1) determines fraction of portfolio allocated");
        out.println("- **Period multiplier:** Position size scaled by trading period multiplier (0.25x to 1.0x)");
        out.println("- **Commission:** 0.1% per trade (Binance taker fee)");
        out.println("- **Minimum trade:** $10 notional to avoid excessive micro-trading");
        out.println("- **Compound mode:** Profits reinvested, portfolio value compounds over the full period");
        out.println("- **Daily reset mode:** Portfolio reset to $1,000 each day; total return = sum of daily P&Ls");
        out.println("- **Execution:** Trades executed at candle close price (simplified — no slippage model)");
        out.println("- **Short selling:** Allowed (negative signal = short position)");
        out.println();
        out.println("### Limitations");
        out.println();
        out.println("- Uses 1-hour candles only — does not capture intra-bar price action");
        out.println("- No slippage model — real execution would have some slippage, especially for larger positions");
        out.println("- Strategies originally designed for tick-level quotes; 1-hour granularity may underperform");
        out.println("- Past performance does not guarantee future results");

        out.flush();
        return sw.toString();
    }
}
