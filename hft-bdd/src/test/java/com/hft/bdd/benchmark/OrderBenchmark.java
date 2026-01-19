package com.hft.bdd.benchmark;

import com.hft.core.model.*;
import com.hft.core.model.ObjectPool;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for Order operations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class OrderBenchmark {

    private ObjectPool<Order> orderPool;
    private Symbol symbol;
    private Order templateOrder;

    @Setup
    public void setup() {
        orderPool = new ObjectPool<>(Order::new, 1000);
        symbol = new Symbol("AAPL", Exchange.ALPACA);
        templateOrder = new Order()
                .symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);
    }

    @Benchmark
    public Order createNewOrder() {
        return new Order()
                .symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(15000)
                .quantity(100);
    }

    @Benchmark
    public Order acquireFromPool() {
        Order order = orderPool.acquire();
        order.setSymbol(symbol);
        order.setSide(OrderSide.BUY);
        order.setType(OrderType.LIMIT);
        order.setPrice(15000);
        order.setQuantity(100);
        return order;
    }

    @Benchmark
    public void acquireAndReleasePool(Blackhole bh) {
        Order order = orderPool.acquire();
        order.setSymbol(symbol);
        order.setSide(OrderSide.BUY);
        order.setType(OrderType.LIMIT);
        order.setPrice(15000);
        order.setQuantity(100);
        bh.consume(order);
        orderPool.release(order);
    }

    @Benchmark
    public void markOrderSubmitted(Blackhole bh) {
        Order order = orderPool.acquire();
        order.setSymbol(symbol);
        order.setSide(OrderSide.BUY);
        order.setType(OrderType.MARKET);
        order.setQuantity(100);
        order.markSubmitted();
        bh.consume(order);
        orderPool.release(order);
    }

    @Benchmark
    public void markOrderAccepted(Blackhole bh) {
        Order order = orderPool.acquire();
        order.setSymbol(symbol);
        order.setSide(OrderSide.BUY);
        order.setType(OrderType.MARKET);
        order.setQuantity(100);
        order.markSubmitted();
        order.markAccepted("EX12345");
        bh.consume(order);
        orderPool.release(order);
    }

    @Benchmark
    public void fullOrderLifecycle(Blackhole bh) {
        Order order = orderPool.acquire();
        order.setSymbol(symbol);
        order.setSide(OrderSide.BUY);
        order.setType(OrderType.MARKET);
        order.setQuantity(100);
        order.markSubmitted();
        order.markAccepted("EX12345");
        order.markFilled(100, 15000);
        bh.consume(order);
        orderPool.release(order);
    }

    @Benchmark
    public void copyOrder(Blackhole bh) {
        Order order = orderPool.acquire();
        order.copyFrom(templateOrder);
        bh.consume(order);
        orderPool.release(order);
    }

    @Benchmark
    public void partialFillsWithAveraging(Blackhole bh) {
        Order order = orderPool.acquire();
        order.setSymbol(symbol);
        order.setSide(OrderSide.BUY);
        order.setType(OrderType.LIMIT);
        order.setPrice(15000);
        order.setQuantity(100);
        order.markSubmitted();
        order.markAccepted("EX12345");
        order.markPartiallyFilled(30, 14990);
        order.markPartiallyFilled(30, 15000);
        order.markFilled(40, 15010);
        bh.consume(order.getAverageFilledPrice());
        orderPool.release(order);
    }
}
