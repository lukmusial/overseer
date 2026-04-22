package com.hft.bdd.benchmark;

import com.hft.core.model.*;
import com.hft.engine.service.PositionManager;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Reproduces cache-line false sharing on {@link PositionManager}'s aggregate
 * P&L counters.
 *
 * <p>Scenario: the Disruptor consumer thread writes the four {@code totalRealizedPnl},
 * {@code totalUnrealizedPnl}, {@code cachedRealizedPnlCents}, {@code cachedUnrealizedPnlCents}
 * fields on every non-flat quote. UI snapshot threads, Tomcat workers, and the
 * scheduled risk checker read those same fields at far lower frequency but
 * still frequently enough that the CPU's cache coherence protocol bounces the
 * line they sit on between cores.
 *
 * <p>JMH setup:
 * <ul>
 *   <li>{@code @Group("positionAggregate")} with separate writer and reader methods.</li>
 *   <li>{@code @GroupThreads(1)} each — one dedicated writer, one dedicated reader.
 *       JMH reports per-method ns/op so we see which side pays the
 *       cache-line-bouncing tax.</li>
 * </ul>
 *
 * <p>Measures the same {@code PositionManager} before and after Phase 5's
 * padding change. Before: four volatile longs share at most two cache lines.
 * After: each lives in its own {@code PaddedVolatileLong} instance on its own
 * line, so the writer's store to (say) {@code cachedUnrealizedPnlCents} no
 * longer invalidates the reader's copy of {@code cachedRealizedPnlCents}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class FieldContentionBenchmark {

    private PositionManager positionManager;
    private Symbol symbol;
    private long tick;

    @Setup(Level.Trial)
    public void setup() {
        positionManager = new PositionManager();
        symbol = new Symbol("BTCUSDT", Exchange.BINANCE);

        // Open a non-flat position so updateMarketValue actually writes the
        // aggregate counters (short-circuits otherwise).
        positionManager.restorePosition(
                symbol,
                100_000_000L,     // 1 BTC
                67_000_00000000L, // avg entry
                67_000_00000000L, // total cost
                1_000L,           // realized
                67_000_00000000L, // market value
                67_000_00000000L, // current price
                100_000_000,      // price scale
                System.nanoTime()
        );
    }

    /**
     * Writer thread: mimics the Disruptor consumer applying a mark-to-market
     * update per quote. Each call writes all four aggregate longs in
     * {@code PositionManager}.
     */
    @Benchmark
    @Group("positionAggregate")
    @GroupThreads(1)
    public void writeAggregate() {
        // Alternate price to keep unrealizedPnl actually changing — a no-op
        // update would let the CPU speculate and skip the store.
        long price = 67_000_00000000L + ((tick++) & 0xFF);
        positionManager.updateMarketValue(symbol, price);
    }

    /**
     * Reader thread: mimics UI / risk-checker reads of the aggregate P&L.
     * Hits all four volatile fields per invocation.
     */
    @Benchmark
    @Group("positionAggregate")
    @GroupThreads(1)
    public void readAggregate(Blackhole bh) {
        bh.consume(positionManager.getTotalRealizedPnlCents());
        bh.consume(positionManager.getTotalUnrealizedPnlCents());
        bh.consume(positionManager.getTotalPnlCents());
    }
}
