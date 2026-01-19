package com.hft.bdd;

import com.hft.core.metrics.LatencyHistogram;
import com.hft.core.metrics.OrderMetrics;
import com.hft.core.model.*;
import org.junit.jupiter.api.Test;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Simple performance tests to measure throughput and latency.
 * Not using JMH to avoid plugin compatibility issues.
 */
public class PerformanceTest {

    private static final int WARMUP_ITERATIONS = 100_000;
    private static final int BENCHMARK_ITERATIONS = 1_000_000;
    private static final NumberFormat nf = NumberFormat.getInstance(Locale.US);

    @Test
    void measureOrderCreationThroughput() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            Order order = new Order()
                    .symbol(symbol)
                    .side(OrderSide.BUY)
                    .type(OrderType.LIMIT)
                    .price(15000)
                    .quantity(100);
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            Order order = new Order()
                    .symbol(symbol)
                    .side(OrderSide.BUY)
                    .type(OrderType.LIMIT)
                    .price(15000)
                    .quantity(100);
        }
        long elapsed = System.nanoTime() - start;

        printResults("Order Creation", BENCHMARK_ITERATIONS, elapsed);
    }

    @Test
    void measureOrderPoolThroughput() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);
        ObjectPool<Order> pool = new ObjectPool<>(Order::new, 1024);
        pool.preallocate(512);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            Order order = pool.acquire();
            order.symbol(symbol).side(OrderSide.BUY).type(OrderType.LIMIT).price(15000).quantity(100);
            pool.release(order);
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            Order order = pool.acquire();
            order.symbol(symbol).side(OrderSide.BUY).type(OrderType.LIMIT).price(15000).quantity(100);
            pool.release(order);
        }
        long elapsed = System.nanoTime() - start;

        printResults("Order Pool Acquire/Release", BENCHMARK_ITERATIONS, elapsed);
    }

    @Test
    void measureOrderLifecycleThroughput() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);
        Order order = new Order();

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            order.reset();
            order.symbol(symbol).side(OrderSide.BUY).type(OrderType.LIMIT).price(15000).quantity(100);
            order.markSubmitted();
            order.markAccepted("EX" + i);
            order.markFilled(100, 15000);
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            order.reset();
            order.symbol(symbol).side(OrderSide.BUY).type(OrderType.LIMIT).price(15000).quantity(100);
            order.markSubmitted();
            order.markAccepted("EX" + i);
            order.markFilled(100, 15000);
        }
        long elapsed = System.nanoTime() - start;

        printResults("Order Full Lifecycle", BENCHMARK_ITERATIONS, elapsed);
    }

    @Test
    void measureQuoteProcessingThroughput() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);
        Quote quote = new Quote();

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            quote.reset();
            quote.setSymbol(symbol);
            quote.setBidPrice(15000 + i % 100);
            quote.setAskPrice(15010 + i % 100);
            quote.setBidSize(100);
            quote.setAskSize(150);
            quote.setTimestamp(System.nanoTime());
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            quote.reset();
            quote.setSymbol(symbol);
            quote.setBidPrice(15000 + i % 100);
            quote.setAskPrice(15010 + i % 100);
            quote.setBidSize(100);
            quote.setAskSize(150);
            quote.setTimestamp(System.nanoTime());
        }
        long elapsed = System.nanoTime() - start;

        printResults("Quote Processing", BENCHMARK_ITERATIONS, elapsed);
    }

    @Test
    void measurePositionPnLCalculation() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);
        Position position = new Position(symbol);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            position.reset();
            Trade buy = createTrade(symbol, OrderSide.BUY, 100, 15000);
            position.applyTrade(buy);
            position.updateMarketValue(15100);
            long pnl = position.getUnrealizedPnl();
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            position.reset();
            Trade buy = createTrade(symbol, OrderSide.BUY, 100, 15000);
            position.applyTrade(buy);
            position.updateMarketValue(15100);
            long pnl = position.getUnrealizedPnl();
        }
        long elapsed = System.nanoTime() - start;

        printResults("Position P&L Calculation", BENCHMARK_ITERATIONS, elapsed);
    }

    @Test
    void measureMetricsRecording() {
        OrderMetrics metrics = new OrderMetrics();

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            metrics.recordOrderSubmitted();
            metrics.recordSubmitLatency(1000 + i % 1000);
            metrics.recordOrderAccepted(500 + i % 500);
        }

        metrics.reset();

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            metrics.recordOrderSubmitted();
            metrics.recordSubmitLatency(1000 + i % 1000);
            metrics.recordOrderAccepted(500 + i % 500);
        }
        long elapsed = System.nanoTime() - start;

        printResults("Metrics Recording (3 ops)", BENCHMARK_ITERATIONS, elapsed);
    }

    @Test
    void measureLatencyHistogram() {
        LatencyHistogram histogram = new LatencyHistogram();

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            histogram.record(1000 + i % 10000);
        }

        histogram.reset();

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            histogram.record(1000 + i % 10000);
        }
        long elapsed = System.nanoTime() - start;

        printResults("Latency Histogram Record", BENCHMARK_ITERATIONS, elapsed);

        // Also measure stats retrieval
        start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            histogram.getStats();
        }
        elapsed = System.nanoTime() - start;

        printResults("Histogram Stats Retrieval", 10000, elapsed);
    }

    private Trade createTrade(Symbol symbol, OrderSide side, long qty, long price) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSide(side);
        trade.setQuantity(qty);
        trade.setPrice(price);
        trade.setExecutedAt(System.nanoTime());
        return trade;
    }

    private void printResults(String name, int iterations, long elapsedNanos) {
        double avgNanos = (double) elapsedNanos / iterations;
        double opsPerSecond = 1_000_000_000.0 / avgNanos;

        System.out.println();
        System.out.println("=== " + name + " ===");
        System.out.println("  Iterations:     " + nf.format(iterations));
        System.out.println("  Total time:     " + nf.format(elapsedNanos / 1_000_000) + " ms");
        System.out.println("  Avg latency:    " + String.format("%.1f ns", avgNanos));
        System.out.println("  Throughput:     " + nf.format((long) opsPerSecond) + " ops/sec");
    }
}
