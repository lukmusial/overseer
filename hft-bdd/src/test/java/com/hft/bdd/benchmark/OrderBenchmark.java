package com.hft.bdd.benchmark;

import com.hft.core.model.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for order creation and lifecycle operations.
 * Measures latency of critical path operations.
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
public class OrderBenchmark {

    private Symbol symbol;
    private ObjectPool<Order> orderPool;
    private Order reusableOrder;

    @Setup
    public void setup() {
        symbol = new Symbol("AAPL", Exchange.ALPACA);
        orderPool = new ObjectPool<>(Order::new, 1024);
        orderPool.preallocate(512);
        reusableOrder = new Order();
    }

    /**
     * Benchmark: Create a new order object from scratch.
     */
    @Benchmark
    public Order createNewOrder() {
        return new Order()
                .symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100)
                .timeInForce(TimeInForce.DAY);
    }

    /**
     * Benchmark: Acquire order from pool (low-alloc path).
     */
    @Benchmark
    public Order acquireFromPool() {
        Order order = orderPool.acquire();
        order.symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100)
                .timeInForce(TimeInForce.DAY);
        return order;
    }

    /**
     * Benchmark: Acquire and release order from pool.
     */
    @Benchmark
    public void acquireAndReleasePool(Blackhole bh) {
        Order order = orderPool.acquire();
        order.symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);
        bh.consume(order.getClientOrderId());
        orderPool.release(order);
    }

    /**
     * Benchmark: Mark order submitted (timestamp capture).
     */
    @Benchmark
    public void markOrderSubmitted(Blackhole bh) {
        reusableOrder.reset();
        reusableOrder.markSubmitted();
        bh.consume(reusableOrder.getSubmittedAt());
    }

    /**
     * Benchmark: Mark order accepted.
     */
    @Benchmark
    public void markOrderAccepted(Blackhole bh) {
        reusableOrder.reset();
        reusableOrder.markSubmitted();
        reusableOrder.markAccepted("EX123");
        bh.consume(reusableOrder.getAckLatencyNanos());
    }

    /**
     * Benchmark: Full order lifecycle (submit -> accept -> fill).
     */
    @Benchmark
    public void fullOrderLifecycle(Blackhole bh) {
        reusableOrder.reset();
        reusableOrder.symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);

        reusableOrder.markSubmitted();
        reusableOrder.markAccepted("EX123");
        reusableOrder.markFilled(100, 15000);

        bh.consume(reusableOrder.getFillLatencyNanos());
    }

    /**
     * Benchmark: Partial fills with average price calculation.
     */
    @Benchmark
    public void partialFillsWithAveraging(Blackhole bh) {
        reusableOrder.reset();
        reusableOrder.symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);

        reusableOrder.markSubmitted();
        reusableOrder.markAccepted("EX123");
        reusableOrder.markPartiallyFilled(30, 15000);
        reusableOrder.markPartiallyFilled(30, 15010);
        reusableOrder.markPartiallyFilled(40, 14990);

        bh.consume(reusableOrder.getAverageFilledPrice());
    }

    /**
     * Benchmark: Order copy operation.
     */
    @Benchmark
    public void copyOrder(Blackhole bh) {
        Order source = new Order()
                .symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);
        source.markSubmitted();
        source.markAccepted("EX123");

        Order copy = new Order();
        copy.copyFrom(source);
        bh.consume(copy.getClientOrderId());
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(OrderBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
