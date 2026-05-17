package com.hft.bdd.benchmark;

import com.hft.api.persistence.AsyncPositionPersister;
import com.hft.core.model.*;
import com.hft.engine.TradingEngine;
import com.hft.engine.event.TradingEvent;
import com.hft.engine.service.RiskManager;
import com.hft.exchange.binance.parser.BinanceMessageParser;
import com.hft.exchange.binance.parser.ManualBinanceParser;
import com.hft.persistence.chronicle.ChroniclePositionSnapshotStore;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Proves the Phase 3 {@link AsyncPositionPersister} actually decouples the
 * Disruptor consumer thread from Chronicle I/O under disk pressure.
 *
 * <p>Setup differs from {@link EndToEndLatencyBenchmark} in two ways:
 * <ol>
 *   <li>An actual {@link ChroniclePositionSnapshotStore} is backing a real on-disk
 *       queue (@TempDir-style path, cleaned up on teardown).</li>
 *   <li>A non-flat {@code Position} is pre-created so that
 *       {@code PositionHandler.updateMarketValue} actually fires the listener
 *       chain rather than short-circuiting on {@code position == null}.</li>
 * </ol>
 *
 * <p>{@code @Param("mode")}:
 * <ul>
 *   <li>{@code inline}: the pre-Phase-3 behaviour — listener writes to Chronicle
 *       synchronously on the consumer thread.</li>
 *   <li>{@code async}: the Phase 3 behaviour — listener submits to
 *       {@code AsyncPositionPersister} and returns immediately.</li>
 * </ul>
 *
 * <p>{@code @Param("diskLoad")}:
 * <ul>
 *   <li>{@code quiet}: no extra disk activity.</li>
 *   <li>{@code noisy}: a background thread continuously writes + fsyncs a 1 MB
 *       scratch file in the same temp directory. On Linux this produces clear
 *       contention; on macOS the OS batches fsyncs aggressively so the effect
 *       is muted — the benchmark still shows non-zero signal.</li>
 * </ul>
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class DiskContentionBenchmark {

    private static final String BINANCE_BOOK_TICKER_JSON =
            "{\"stream\":\"btcusdt@bookTicker\",\"data\":{\"s\":\"BTCUSDT\"," +
            "\"b\":\"67432.15000000\",\"a\":\"67432.16000000\"," +
            "\"B\":\"1.23400000\",\"A\":\"4.56700000\"}}";

    @Param({"inline", "async"})
    public String mode;

    @Param({"quiet", "noisy"})
    public String diskLoad;

    private Path tempDir;
    private Path scratchFile;
    private ChroniclePositionSnapshotStore store;
    private AsyncPositionPersister asyncPersister;
    private TradingEngine engine;
    private BinanceMessageParser parser;
    private ObjectPool<Quote> quotePool;
    private Symbol symbol;
    private long publishedEvents;
    private TerminalCounter terminal;

    // Background disk-pressure thread
    private Thread fsyncThread;
    private final AtomicBoolean noisyRunning = new AtomicBoolean(false);

    /**
     * Copy of the pattern in {@link EndToEndLatencyBenchmark} — a volatile counter
     * updated by a handler appended after MetricsHandler. Lets us busy-spin until
     * the consumer has drained a given event.
     */
    private static final class TerminalCounter implements EventHandler<TradingEvent> {
        private volatile long processed;
        @Override public void onEvent(TradingEvent event, long sequence, boolean endOfBatch) {
            processed = sequence + 1;
        }
        long processed() { return processed; }
    }

    @Setup(Level.Trial)
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("hft-disk-bench-");
        scratchFile = tempDir.resolve("scratch.bin");

        parser = new ManualBinanceParser();
        symbol = new Symbol("BTCUSDT", Exchange.BINANCE);
        quotePool = new ObjectPool<>(Quote::new, 1024);

        store = new ChroniclePositionSnapshotStore(tempDir);

        engine = new TradingEngine(
                RiskManager.RiskLimits.defaults(),
                1024 * 64,
                new YieldingWaitStrategy()
        );
        terminal = new TerminalCounter();
        engine.addTerminalHandler(terminal);

        // Pre-create a non-flat position so PositionHandler actually fires the listener.
        engine.getPositionManager().restorePosition(
                symbol,
                100_000_000L,     // 1 BTC in satoshis
                67_000_00000000L, // entry price
                67_000_00000000L, // total cost (per-share, scale handled internally)
                0L,               // realized
                67_000_00000000L, // market value
                67_000_00000000L, // current price
                100_000_000,      // price scale
                System.nanoTime()
        );

        // Install the configured persistence listener.
        switch (mode) {
            case "inline":
                engine.getPositionManager().addPositionListener(position ->
                        store.saveSnapshot(position, System.nanoTime()));
                break;
            case "async":
                asyncPersister = new AsyncPositionPersister(store, 65_536);
                engine.getPositionManager().addPositionListener(asyncPersister::submit);
                break;
            default:
                throw new IllegalArgumentException("unknown mode: " + mode);
        }

        engine.start();

        if ("noisy".equals(diskLoad)) {
            startFsyncPressure();
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException, InterruptedException {
        stopFsyncPressure();
        engine.stop();
        if (asyncPersister != null) asyncPersister.close();
        store.close();
        // Best-effort cleanup of temp files.
        if (Files.exists(tempDir)) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
        }
    }

    private void startFsyncPressure() {
        noisyRunning.set(true);
        fsyncThread = new Thread(() -> {
            ByteBuffer buf = ByteBuffer.allocate(1024 * 1024);
            for (int i = 0; i < buf.capacity(); i++) buf.put((byte) i);
            while (noisyRunning.get()) {
                try (FileChannel ch = FileChannel.open(scratchFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    buf.rewind();
                    ch.write(buf);
                    ch.force(true);
                } catch (IOException ignored) {
                    // benchmark best-effort; disk errors here are not interesting signal
                }
            }
        }, "disk-pressure");
        fsyncThread.setDaemon(true);
        fsyncThread.start();
    }

    private void stopFsyncPressure() throws InterruptedException {
        if (!noisyRunning.getAndSet(false)) return;
        if (fsyncThread != null) {
            fsyncThread.join(5_000);
        }
    }

    /**
     * Publishes a quote on the held symbol, exercising
     * {@code PositionHandler.updateMarketValue -> persistence listener}. The hot
     * path includes:
     * <ul>
     *   <li>{@code inline} mode: the Chronicle {@code saveSnapshot} call runs on
     *       the Disruptor consumer thread and blocks the round-trip until the
     *       queue write completes.</li>
     *   <li>{@code async} mode: {@code saveSnapshot} runs on the persister
     *       thread; the consumer thread just offers to the handoff queue.</li>
     * </ul>
     */
    @Benchmark
    public void signalWithActivePosition(Blackhole bh) {
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

        // Spin until the terminal counter observes this event. PositionHandler
        // fires the listener earlier in the chain, so by the time terminal sees
        // the sequence, the listener has definitely been invoked (and in the
        // inline variant, the Chronicle write has completed).
        while (terminal.processed() < publishedEvents) {
            Thread.onSpinWait();
        }

        bh.consume(quote);
        quotePool.release(quote);
    }
}
