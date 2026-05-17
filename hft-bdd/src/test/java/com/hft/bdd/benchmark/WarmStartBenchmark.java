package com.hft.bdd.benchmark;

import com.hft.core.model.*;
import com.hft.engine.TradingEngine;
import com.hft.engine.service.RiskManager;
import com.hft.exchange.binance.parser.BinanceMessageParser;
import com.hft.exchange.binance.parser.ManualBinanceParser;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Measures cold-start latency: on a freshly-booted JVM, how long does it take
 * to push the first batch of real events through the engine's hot path?
 *
 * <p>The regular {@code EndToEndLatencyBenchmark} cannot see the Phase 3
 * {@link TradingEngine#warmUp(int)} benefit because JMH's own {@code @Warmup(5,1s)}
 * runs real events through the engine before the measurement window, which
 * itself does the JIT warming we're trying to measure.
 *
 * <p>This benchmark uses {@link Mode#SingleShotTime} — each fork is a fresh JVM,
 * each fork runs the {@code @Benchmark} method exactly once, reports the time,
 * and exits. {@code @Fork(20)} gives us 20 cold-start samples per param value.
 *
 * <p>{@code @Param("warmupEnabled")}:
 * <ul>
 *   <li>{@code false}: engine started, benchmark fires 1000 real events. This is
 *       the "cold JIT" condition — the first few hundred events pay the C2
 *       compilation cost.</li>
 *   <li>{@code true}: engine started, {@code warmUp(10_000)} called, then 1000
 *       real events. Warmup should have pushed C2 compilation off the
 *       measurement window.</li>
 * </ul>
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(20)
public class WarmStartBenchmark {

    private static final String BINANCE_BOOK_TICKER_JSON =
            "{\"stream\":\"btcusdt@bookTicker\",\"data\":{\"s\":\"BTCUSDT\"," +
            "\"b\":\"67432.15000000\",\"a\":\"67432.16000000\"," +
            "\"B\":\"1.23400000\",\"A\":\"4.56700000\"}}";

    private static final int BATCH_SIZE = 1000;
    private static final int WARMUP_ITERATIONS = 10_000;

    @Param({"false", "true"})
    public boolean warmupEnabled;

    private BinanceMessageParser parser;
    private ObjectPool<Quote> quotePool;
    private Symbol symbol;
    private TradingEngine engine;

    /**
     * Runs once per fork — so with {@code @Fork(20)} we get 20 cold JVMs. Only
     * this setup and the single @Benchmark invocation contribute to the
     * measurement; everything else (JMH iteration plumbing) is outside the
     * timed window.
     */
    @Setup(Level.Trial)
    public void setup() {
        parser = new ManualBinanceParser();
        symbol = new Symbol("BTCUSDT", Exchange.BINANCE);
        quotePool = new ObjectPool<>(Quote::new, 4096);

        engine = new TradingEngine(
                RiskManager.RiskLimits.defaults(),
                1024 * 64,
                new YieldingWaitStrategy()
        );
        engine.start();
        if (warmupEnabled) {
            engine.warmUp(WARMUP_ITERATIONS);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        engine.stop();
    }

    /**
     * Single-shot: fires BATCH_SIZE events through the hot path. The elapsed
     * time is what JMH reports. Any C2 compilation that happens during these
     * 1000 events is counted against us when {@code warmupEnabled=false}.
     */
    @Benchmark
    public void firstBatchAfterStart(Blackhole bh) {
        for (int i = 0; i < BATCH_SIZE; i++) {
            BinanceMessageParser.TickerFields fields = parser.parseTicker(BINANCE_BOOK_TICKER_JSON);
            if (fields == null) continue;

            Quote quote = quotePool.acquire();
            quote.setSymbol(symbol);
            quote.setBidPrice(fields.bidPrice());
            quote.setAskPrice(fields.askPrice());
            quote.setBidSize(fields.bidSize());
            quote.setAskSize(fields.askSize());
            quote.setTimestamp(System.nanoTime());
            quote.setReceivedAt(System.nanoTime());
            quote.setPriceScale(100_000_000);

            engine.onQuoteUpdate(quote);

            bh.consume(quote);
            quotePool.release(quote);
        }
    }
}
