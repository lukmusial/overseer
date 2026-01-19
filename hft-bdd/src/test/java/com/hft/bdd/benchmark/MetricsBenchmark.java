package com.hft.bdd.benchmark;

import com.hft.core.metrics.LatencyHistogram;
import com.hft.core.metrics.LatencyStats;
import com.hft.core.metrics.OrderMetrics;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for Metrics operations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MetricsBenchmark {

    private OrderMetrics metrics;
    private LatencyHistogram histogram;
    private Random random;

    @Setup
    public void setup() {
        metrics = new OrderMetrics();
        histogram = new LatencyHistogram();
        random = new Random(42);

        // Pre-populate with some data
        for (int i = 0; i < 1000; i++) {
            histogram.record(random.nextLong(1_000_000)); // 0-1ms
        }
    }

    @Benchmark
    public void recordLatency(Blackhole bh) {
        histogram.record(500_000); // 500us
        bh.consume(histogram);
    }

    @Benchmark
    public void recordRandomLatency(Blackhole bh) {
        histogram.record(random.nextLong(1_000_000));
        bh.consume(histogram);
    }

    @Benchmark
    public long getP99Percentile() {
        return histogram.getPercentile(99);
    }

    @Benchmark
    public void getHistogramStats(Blackhole bh) {
        LatencyStats stats = histogram.getStats();
        bh.consume(stats.min());
        bh.consume(stats.max());
        bh.consume(stats.mean());
        bh.consume(stats.p50());
        bh.consume(stats.p99());
    }

    @Benchmark
    public void recordOrderSubmitted(Blackhole bh) {
        metrics.recordOrderSubmitted();
        bh.consume(metrics.getOrdersSubmitted());
    }

    @Benchmark
    public void recordOrderFillComplete(Blackhole bh) {
        long latency = 100_000; // 100us
        metrics.recordOrderFilled(latency, 100, 1500000);
        bh.consume(metrics.getOrdersFilled());
    }

    @Benchmark
    public void recordCompleteOrderFlow(Blackhole bh) {
        metrics.recordOrderSubmitted();
        metrics.recordSubmitLatency(50_000);
        metrics.recordOrderAccepted(100_000);
        metrics.recordOrderFilled(200_000, 100, 1500000);
        bh.consume(metrics);
    }

    @Benchmark
    public void takeMetricsSnapshot(Blackhole bh) {
        OrderMetrics.MetricsSnapshot snapshot = metrics.snapshot();
        bh.consume(snapshot);
    }

    @Benchmark
    @Threads(4)
    public void concurrent(Blackhole bh) {
        metrics.recordOrderSubmitted();
        metrics.recordSubmitLatency(random.nextLong(100_000));
        bh.consume(metrics.getOrdersSubmitted());
    }
}
