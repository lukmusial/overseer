package com.hft.bdd.benchmark;

import com.hft.core.model.*;
import com.hft.engine.TradingEngine;
import com.hft.engine.event.TradingEvent;
import com.hft.engine.service.RiskManager;
import com.hft.engine.thread.AffinityStrategyType;
import com.hft.engine.thread.PinnedThreadFactory;
import com.hft.exchange.binance.parser.BinanceMessageParser;
import com.hft.exchange.binance.parser.ManualBinanceParser;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * End-to-end latency benchmark for the HFT hot path:
 *   raw ticker string -> parse -> Quote -> ring-buffer publish -> full handler chain
 *   -> synthetic terminal handler -> caller observes completion.
 *
 * <p>Unlike {@link PipelineBenchmark}, this benchmark blocks until the Disruptor consumer
 * has actually processed the event — so work executed on the consumer thread (the one we
 * pin via affinity) is part of the measured interval. Without this, affinity is invisible
 * to the measurement.
 *
 * <p>The {@link #pinConsumer} @Param toggles pinning so a single JMH run produces two
 * directly comparable datasets. On macOS the pinned variant is expected to perform
 * identically to the unpinned variant — OpenHFT cannot pin under Darwin. On Linux the
 * pinned variant should show lower p99/p99.9 and reduced stddev.
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 20, time = 1)
@Fork(3)
public class EndToEndLatencyBenchmark {

    private static final String BINANCE_BOOK_TICKER_JSON =
            "{\"stream\":\"btcusdt@bookTicker\",\"data\":{\"s\":\"BTCUSDT\"," +
            "\"b\":\"67432.15000000\",\"a\":\"67432.16000000\"," +
            "\"B\":\"1.23400000\",\"A\":\"4.56700000\"}}";

    @Param({"false", "true"})
    public boolean pinConsumer;

    private TradingEngine engine;
    private BinanceMessageParser parser;
    private ObjectPool<Quote> quotePool;
    private Symbol symbol;
    private TerminalCounter terminal;
    private long publishedEvents;

    /**
     * Terminal handler appended after MetricsHandler. Writes a monotonic counter the
     * benchmark thread busy-spins on — cheaper than a latch, avoids park/unpark jitter.
     */
    private static final class TerminalCounter implements EventHandler<TradingEvent> {
        private volatile long processed;
        @Override
        public void onEvent(TradingEvent event, long sequence, boolean endOfBatch) {
            processed = sequence + 1;
        }
        long processed() { return processed; }
    }

    @Setup(Level.Trial)
    public void setup() {
        parser = new ManualBinanceParser();
        symbol = new Symbol("BTCUSDT", Exchange.BINANCE);
        quotePool = new ObjectPool<>(Quote::new, 1024);

        var threadFactory = new PinnedThreadFactory("bench-consumer",
                pinConsumer, AffinityStrategyType.ANY);

        engine = new TradingEngine(
                RiskManager.RiskLimits.defaults(),
                1024 * 64,
                new YieldingWaitStrategy(),
                threadFactory
        );
        terminal = new TerminalCounter();
        engine.addTerminalHandler(terminal);
        engine.start();
        publishedEvents = 0;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        engine.stop();
    }

    /**
     * Full hot path: parse raw ticker JSON, build pooled Quote, publish to engine,
     * await the Disruptor consumer finishing the handler chain for this event.
     *
     * <p>This is the "market signal → engine consumed" round-trip. Pinning the consumer
     * thread affects the Disruptor handler execution window, which is the tail of this
     * measurement.
     */
    @Benchmark
    public void signalToEngineConsumed(Blackhole bh) {
        BinanceMessageParser.TickerFields fields = parser.parseTicker(BINANCE_BOOK_TICKER_JSON);
        if (fields == null) return;

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
        publishedEvents++;

        // Busy-spin until the Disruptor consumer finishes processing this event.
        // Cheap on macOS; on Linux with pinning this yields tight percentile bounds.
        while (terminal.processed() < publishedEvents) {
            Thread.onSpinWait();
        }

        bh.consume(quote);
        quotePool.release(quote);
    }

    /**
     * Extends the above by also submitting an order after the quote — simulating a
     * strategy reaction. Measures the full "signal → order published and consumed"
     * window. Both events traverse risk → order → position → metrics → terminal.
     */
    @Benchmark
    public void signalToOrderConsumed(Blackhole bh) {
        // Reset daily counters occasionally to avoid hitting risk limits during long runs.
        // resetDailyCounters is cheap (atomic stores); the extra variance it adds is
        // negligible relative to the handler-chain cost we're measuring.
        if ((publishedEvents & 0xFFF) == 0) {
            engine.resetDailyCounters();
        }

        BinanceMessageParser.TickerFields fields = parser.parseTicker(BINANCE_BOOK_TICKER_JSON);
        if (fields == null) return;

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

        Order order = new Order();
        order.setSymbol(symbol);
        order.setSide(OrderSide.BUY);
        order.setType(OrderType.LIMIT);
        order.setPrice(fields.bidPrice());
        order.setQuantity(100_000_000L); // 1.0 BTC in satoshis
        order.setPriceScale(100_000_000);
        String rejection = engine.submitOrder(order);

        publishedEvents += (rejection == null) ? 2 : 1;

        while (terminal.processed() < publishedEvents) {
            Thread.onSpinWait();
        }

        bh.consume(rejection);
        bh.consume(quote);
        quotePool.release(quote);
    }
}
