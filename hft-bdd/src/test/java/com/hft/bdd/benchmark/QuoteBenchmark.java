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
 * JMH benchmarks for quote processing operations.
 * Critical for market data handling performance.
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
public class QuoteBenchmark {

    private Symbol symbol;
    private Quote sourceQuote;
    private Quote targetQuote;
    private ObjectPool<Quote> quotePool;

    @Setup
    public void setup() {
        symbol = new Symbol("AAPL", Exchange.ALPACA);
        sourceQuote = new Quote(symbol, 14995, 15005, 1000, 500, System.nanoTime());
        targetQuote = new Quote();
        quotePool = new ObjectPool<>(() -> {
            Quote q = new Quote();
            return q;
        }, 1024);
        quotePool.preallocate(512);
    }

    /**
     * Benchmark: Create new quote object.
     */
    @Benchmark
    public Quote createNewQuote() {
        return new Quote(symbol, 14995, 15005, 1000, 500, System.nanoTime());
    }

    /**
     * Benchmark: Acquire quote from pool.
     */
    @Benchmark
    public Quote acquireQuoteFromPool() {
        Quote quote = quotePool.acquire();
        quote.setSymbol(symbol);
        quote.setBidPrice(14995);
        quote.setAskPrice(15005);
        quote.setBidSize(1000);
        quote.setAskSize(500);
        quote.setTimestamp(System.nanoTime());
        return quote;
    }

    /**
     * Benchmark: Copy quote (for order book updates).
     */
    @Benchmark
    public void copyQuote(Blackhole bh) {
        targetQuote.copyFrom(sourceQuote);
        bh.consume(targetQuote.getBidPrice());
    }

    /**
     * Benchmark: Calculate mid price.
     */
    @Benchmark
    public long calculateMidPrice() {
        return sourceQuote.getMidPrice();
    }

    /**
     * Benchmark: Calculate spread.
     */
    @Benchmark
    public long calculateSpread() {
        return sourceQuote.getSpread();
    }

    /**
     * Benchmark: Quote update with all fields.
     */
    @Benchmark
    public void updateQuoteFields(Blackhole bh) {
        targetQuote.reset();
        targetQuote.setSymbol(symbol);
        targetQuote.setBidPrice(14998);
        targetQuote.setAskPrice(15002);
        targetQuote.setBidSize(1500);
        targetQuote.setAskSize(800);
        targetQuote.setTimestamp(System.nanoTime());
        targetQuote.setSequenceNumber(12345L);
        bh.consume(targetQuote.getMidPrice());
    }

    /**
     * Benchmark: Quote pool acquire and release cycle.
     */
    @Benchmark
    public void quotePoolCycle(Blackhole bh) {
        Quote quote = quotePool.acquire();
        quote.setSymbol(symbol);
        quote.setBidPrice(14995);
        quote.setAskPrice(15005);
        bh.consume(quote.getMidPrice());
        quotePool.release(quote);
    }

    /**
     * Benchmark: Simulate quote tick update.
     */
    @Benchmark
    public void simulateTickUpdate(Blackhole bh) {
        // Simulate receiving a tick and processing it
        long receiveTime = System.nanoTime();

        targetQuote.copyFrom(sourceQuote);
        targetQuote.setBidPrice(targetQuote.getBidPrice() + 1);
        targetQuote.setAskPrice(targetQuote.getAskPrice() + 1);
        targetQuote.setTimestamp(receiveTime);

        bh.consume(targetQuote.getMidPrice());
        bh.consume(targetQuote.getSpread());
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(QuoteBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
