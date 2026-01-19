package com.hft.bdd.benchmark;

import com.hft.core.metrics.LatencyHistogram;
import com.hft.core.metrics.OrderMetrics;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for metrics collection operations.
 * Ensures metrics recording doesn't add significant latency.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgs = {
        "-XX:+UseZGC",
        "-XX:+ZGenerational",
        "-Xms1g",
        "-Xmx1g"
})
public class MetricsBenchmark {

    private OrderMetrics orderMetrics;
    private LatencyHistogram histogram;

    @Setup
    public void setup() {
        orderMetrics = new OrderMetrics();
        histogram = new LatencyHistogram();

        // Pre-warm the metrics with some data
        for (int i = 0; i < 1000; i++) {
            histogram.record(ThreadLocalRandom.current().nextLong(1000, 100000));
        }
    }

    /**
     * Benchmark: Record order submission (atomic increment).
     */
    @Benchmark
    public void recordOrderSubmitted() {
        orderMetrics.recordOrderSubmitted();
    }

    /**
     * Benchmark: Record latency in histogram.
     */
    @Benchmark
    public void recordLatency() {
        histogram.record(5000L); // 5 microseconds
    }

    /**
     * Benchmark: Record latency with random value.
     */
    @Benchmark
    public void recordRandomLatency() {
        long latency = ThreadLocalRandom.current().nextLong(1000, 100000);
        histogram.record(latency);
    }

    /**
     * Benchmark: Record order fill with all metrics.
     */
    @Benchmark
    public void recordOrderFillComplete() {
        orderMetrics.recordOrderFilled(5000, 100, 150000);
    }

    /**
     * Benchmark: Get percentile from histogram.
     */
    @Benchmark
    public long getP99Percentile(Blackhole bh) {
        long p99 = histogram.getPercentile(99);
        bh.consume(p99);
        return p99;
    }

    /**
     * Benchmark: Get full statistics from histogram.
     */
    @Benchmark
    public void getHistogramStats(Blackhole bh) {
        bh.consume(histogram.getStats());
    }

    /**
     * Benchmark: Take a full metrics snapshot.
     */
    @Benchmark
    public void takeMetricsSnapshot(Blackhole bh) {
        bh.consume(orderMetrics.snapshot());
    }

    /**
     * Benchmark: Concurrent metrics recording simulation.
     */
    @Benchmark
    @Group("concurrent")
    @GroupThreads(4)
    public void concurrentRecordSubmit() {
        orderMetrics.recordOrderSubmitted();
        orderMetrics.recordSubmitLatency(ThreadLocalRandom.current().nextLong(1000, 10000));
    }

    /**
     * Benchmark: Combined order flow metrics recording.
     */
    @Benchmark
    public void recordCompleteOrderFlow() {
        long startTime = System.nanoTime();

        orderMetrics.recordOrderSubmitted();
        orderMetrics.recordSubmitLatency(System.nanoTime() - startTime);
        orderMetrics.recordOrderAccepted(1500);
        orderMetrics.recordOrderFilled(3000, 100, 150000);
        orderMetrics.recordRoundTripLatency(System.nanoTime() - startTime);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MetricsBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
