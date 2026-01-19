package com.hft.bdd.benchmark;

import com.hft.core.model.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for Position operations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
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

        sellTrade = new Trade();
        sellTrade.setSymbol(symbol);
        sellTrade.setSide(OrderSide.SELL);
        sellTrade.setQuantity(50);
        sellTrade.setPrice(15500);
    }

    @Setup(Level.Invocation)
    public void resetPosition() {
        position.reset();
        position.setSymbol(symbol);
    }

    @Benchmark
    public void applyBuyTradeToFlat(Blackhole bh) {
        position.applyTrade(buyTrade);
        bh.consume(position.getQuantity());
    }

    @Benchmark
    public void applyTradeToExisting(Blackhole bh) {
        position.applyTrade(buyTrade);
        position.applyTrade(buyTrade);
        bh.consume(position.getAverageEntryPrice());
    }

    @Benchmark
    public void partialCloseWithPnl(Blackhole bh) {
        position.applyTrade(buyTrade);
        position.applyTrade(sellTrade);
        bh.consume(position.getRealizedPnl());
    }

    @Benchmark
    public void calculateTotalPnl(Blackhole bh) {
        position.applyTrade(buyTrade);
        position.updateMarketValue(15500);
        bh.consume(position.getTotalPnl());
    }

    @Benchmark
    public void updateMarketValue(Blackhole bh) {
        position.applyTrade(buyTrade);
        position.updateMarketValue(15100);
        position.updateMarketValue(15200);
        position.updateMarketValue(15300);
        bh.consume(position.getUnrealizedPnl());
    }

    @Benchmark
    public void positionReversal(Blackhole bh) {
        position.applyTrade(buyTrade);

        Trade reversalTrade = new Trade();
        reversalTrade.setSymbol(symbol);
        reversalTrade.setSide(OrderSide.SELL);
        reversalTrade.setQuantity(150);
        reversalTrade.setPrice(15200);

        position.applyTrade(reversalTrade);
        bh.consume(position.isShort());
        bh.consume(position.getRealizedPnl());
    }

    @Benchmark
    public void multipleTradesWithAveraging(Blackhole bh) {
        for (int i = 0; i < 10; i++) {
            Trade trade = new Trade();
            trade.setSymbol(symbol);
            trade.setSide(OrderSide.BUY);
            trade.setQuantity(10);
            trade.setPrice(15000 + i * 10);
            position.applyTrade(trade);
        }
        bh.consume(position.getAverageEntryPrice());
    }
}
