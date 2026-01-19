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
 * JMH benchmarks for position tracking operations.
 * Critical for real-time P&L calculations.
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
public class PositionBenchmark {

    private Symbol symbol;
    private Position position;
    private Trade buyTrade;
    private Trade sellTrade;

    @Setup
    public void setup() {
        symbol = new Symbol("AAPL", Exchange.ALPACA);
        position = new Position(symbol);

        buyTrade = new Trade();
        buyTrade.setSymbol(symbol);
        buyTrade.setSide(OrderSide.BUY);
        buyTrade.setQuantity(100);
        buyTrade.setPrice(15000);
        buyTrade.setExecutedAt(System.nanoTime());

        sellTrade = new Trade();
        sellTrade.setSymbol(symbol);
        sellTrade.setSide(OrderSide.SELL);
        sellTrade.setQuantity(50);
        sellTrade.setPrice(15100);
        sellTrade.setExecutedAt(System.nanoTime());
    }

    @Setup(Level.Invocation)
    public void resetPosition() {
        position.reset();
        position.setSymbol(symbol);
    }

    /**
     * Benchmark: Apply buy trade to flat position.
     */
    @Benchmark
    public void applyBuyTradeToFlat(Blackhole bh) {
        position.applyTrade(buyTrade);
        bh.consume(position.getQuantity());
    }

    /**
     * Benchmark: Apply trade to existing position (add).
     */
    @Benchmark
    public void applyTradeToExisting(Blackhole bh) {
        position.applyTrade(buyTrade);
        position.applyTrade(buyTrade); // Add more
        bh.consume(position.getAverageEntryPrice());
    }

    /**
     * Benchmark: Partial close with P&L calculation.
     */
    @Benchmark
    public void partialCloseWithPnl(Blackhole bh) {
        position.applyTrade(buyTrade);
        position.applyTrade(sellTrade);
        bh.consume(position.getRealizedPnl());
    }

    /**
     * Benchmark: Update market value and unrealized P&L.
     */
    @Benchmark
    public void updateMarketValue(Blackhole bh) {
        position.applyTrade(buyTrade);
        position.updateMarketValue(15200);
        bh.consume(position.getUnrealizedPnl());
    }

    /**
     * Benchmark: Multiple trades with averaging.
     */
    @Benchmark
    public void multipleTradesWithAveraging(Blackhole bh) {
        Trade t1 = new Trade();
        t1.setSymbol(symbol);
        t1.setSide(OrderSide.BUY);
        t1.setQuantity(100);
        t1.setPrice(15000);
        t1.setExecutedAt(System.nanoTime());

        Trade t2 = new Trade();
        t2.setSymbol(symbol);
        t2.setSide(OrderSide.BUY);
        t2.setQuantity(50);
        t2.setPrice(15100);
        t2.setExecutedAt(System.nanoTime());

        Trade t3 = new Trade();
        t3.setSymbol(symbol);
        t3.setSide(OrderSide.SELL);
        t3.setQuantity(75);
        t3.setPrice(15200);
        t3.setExecutedAt(System.nanoTime());

        position.applyTrade(t1);
        position.applyTrade(t2);
        position.applyTrade(t3);

        bh.consume(position.getRealizedPnl());
        bh.consume(position.getQuantity());
    }

    /**
     * Benchmark: Position reversal (long to short).
     */
    @Benchmark
    public void positionReversal(Blackhole bh) {
        position.applyTrade(buyTrade);

        Trade reversalTrade = new Trade();
        reversalTrade.setSymbol(symbol);
        reversalTrade.setSide(OrderSide.SELL);
        reversalTrade.setQuantity(150);
        reversalTrade.setPrice(15100);
        reversalTrade.setExecutedAt(System.nanoTime());

        position.applyTrade(reversalTrade);
        bh.consume(position.getRealizedPnl());
        bh.consume(position.isShort());
    }

    /**
     * Benchmark: Total P&L calculation.
     */
    @Benchmark
    public long calculateTotalPnl(Blackhole bh) {
        position.applyTrade(buyTrade);
        position.applyTrade(sellTrade);
        position.updateMarketValue(15300);
        return position.getTotalPnl();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(PositionBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
