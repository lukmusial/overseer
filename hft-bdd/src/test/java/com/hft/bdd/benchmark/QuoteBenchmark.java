package com.hft.bdd.benchmark;

import com.hft.core.model.Exchange;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;
import com.hft.core.model.ObjectPool;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for Quote operations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QuoteBenchmark {

    private ObjectPool<Quote> quotePool;
    private Symbol symbol;
    private Quote templateQuote;

    @Setup
    public void setup() {
        quotePool = new ObjectPool<>(Quote::new, 10000);
        symbol = new Symbol("AAPL", Exchange.ALPACA);
        templateQuote = new Quote(symbol, 15000, 15001, 1000, 500, System.nanoTime());
    }

    @Benchmark
    public Quote createNewQuote() {
        return new Quote(symbol, 15000, 15001, 1000, 500, System.nanoTime());
    }

    @Benchmark
    public Quote acquireQuoteFromPool() {
        Quote quote = quotePool.acquire();
        quote.setSymbol(symbol);
        quote.setBidPrice(15000);
        quote.setAskPrice(15001);
        quote.setBidSize(1000);
        quote.setAskSize(500);
        quote.setTimestamp(System.nanoTime());
        return quote;
    }

    @Benchmark
    public void quotePoolCycle(Blackhole bh) {
        Quote quote = quotePool.acquire();
        quote.setSymbol(symbol);
        quote.setBidPrice(15000);
        quote.setAskPrice(15001);
        quote.setBidSize(1000);
        quote.setAskSize(500);
        quote.setTimestamp(System.nanoTime());
        bh.consume(quote);
        quotePool.release(quote);
    }

    @Benchmark
    public long calculateMidPrice() {
        return templateQuote.getMidPrice();
    }

    @Benchmark
    public long calculateSpread() {
        return templateQuote.getSpread();
    }

    @Benchmark
    public void copyQuote(Blackhole bh) {
        Quote quote = quotePool.acquire();
        quote.copyFrom(templateQuote);
        bh.consume(quote);
        quotePool.release(quote);
    }

    @Benchmark
    public void updateQuoteFields(Blackhole bh) {
        Quote quote = quotePool.acquire();
        quote.setSymbol(symbol);
        quote.setBidPrice(15000);
        quote.setAskPrice(15001);
        quote.setBidSize(1000);
        quote.setAskSize(500);
        quote.setTimestamp(System.nanoTime());
        quote.setReceivedAt(System.nanoTime());
        quote.setSequenceNumber(12345);
        bh.consume(quote);
        quotePool.release(quote);
    }

    @Benchmark
    public void simulateTickUpdate(Blackhole bh) {
        for (int i = 0; i < 10; i++) {
            Quote quote = quotePool.acquire();
            quote.setSymbol(symbol);
            quote.setBidPrice(15000 + i);
            quote.setAskPrice(15001 + i);
            quote.setBidSize(1000 - i * 10);
            quote.setAskSize(500 + i * 10);
            quote.setTimestamp(System.nanoTime());
            bh.consume(quote.getMidPrice());
            quotePool.release(quote);
        }
    }
}
