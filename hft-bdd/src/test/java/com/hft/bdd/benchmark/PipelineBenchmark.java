package com.hft.bdd.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hft.core.model.*;
import com.hft.core.metrics.OrderMetrics;
import com.hft.core.util.FastDecimalParser;
import com.hft.engine.TradingEngine;
import com.hft.engine.service.RiskManager;
import com.hft.exchange.binance.parser.BinanceMessageParser;
import com.hft.exchange.binance.parser.ManualBinanceParser;
import com.hft.exchange.binance.parser.StreamingBinanceParser;
import com.hft.exchange.binance.parser.JacksonBinanceParser;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for the full hot-path pipeline of the HFT trading system.
 *
 * Measures each stage of the pipeline:
 * WebSocket JSON -> price parsing -> quote construction -> strategy dispatch
 * -> risk check -> ring buffer publish -> handler chain.
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PipelineBenchmark {

    private static final String BINANCE_BOOK_TICKER_JSON =
            "{\"stream\":\"btcusdt@bookTicker\",\"data\":{\"s\":\"BTCUSDT\"," +
            "\"b\":\"67432.15000000\",\"a\":\"67432.16000000\"," +
            "\"B\":\"1.23400000\",\"A\":\"4.56700000\"}}";

    private static final String PRICE_BID = "67432.15000000";
    private static final String PRICE_ASK = "67432.16000000";
    private static final String SIZE_BID = "1.23400000";
    private static final String SIZE_ASK = "4.56700000";

    private static final BigDecimal SATOSHI_MULTIPLIER = BigDecimal.valueOf(100_000_000);

    private ObjectMapper objectMapper;
    private Symbol symbol;
    private ObjectPool<Quote> quotePool;
    private TradingEngine tradingEngine;

    // Parser strategies for comparison benchmarks
    private BinanceMessageParser manualParser;
    private BinanceMessageParser streamingParser;
    private BinanceMessageParser jacksonParser;

    @Setup(Level.Trial)
    public void setup() {
        objectMapper = new ObjectMapper();
        manualParser = new ManualBinanceParser();
        streamingParser = new StreamingBinanceParser();
        jacksonParser = new JacksonBinanceParser();
        symbol = new Symbol("BTCUSDT", Exchange.BINANCE);
        quotePool = new ObjectPool<>(Quote::new, 10_000);
        tradingEngine = new TradingEngine(
                RiskManager.RiskLimits.defaults(),
                1024 * 64,
                new YieldingWaitStrategy()
        );
        tradingEngine.start();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        tradingEngine.stop();
    }

    /**
     * Stage 1: Parse a Binance bookTicker JSON string using Jackson.
     * Extracts ticker symbol and 4 price/size fields.
     */
    @Benchmark
    public void jsonParseFull(Blackhole bh) throws Exception {
        JsonNode root = objectMapper.readTree(BINANCE_BOOK_TICKER_JSON);
        JsonNode data = root.get("data");
        String ticker = data.get("s").asText();
        String bidPrice = data.get("b").asText();
        String askPrice = data.get("a").asText();
        String bidSize = data.get("B").asText();
        String askSize = data.get("A").asText();
        bh.consume(ticker);
        bh.consume(bidPrice);
        bh.consume(askPrice);
        bh.consume(bidSize);
        bh.consume(askSize);
    }

    /**
     * Stage 2: Parse 4 price strings to long using BigDecimal.
     * Converts decimal price strings to minor-unit longs (satoshis for crypto).
     */
    @Benchmark
    public void priceParsingBigDecimal(Blackhole bh) {
        long bid = new BigDecimal(PRICE_BID).multiply(SATOSHI_MULTIPLIER).longValue();
        long ask = new BigDecimal(PRICE_ASK).multiply(SATOSHI_MULTIPLIER).longValue();
        long bidSz = new BigDecimal(SIZE_BID).multiply(SATOSHI_MULTIPLIER).longValue();
        long askSz = new BigDecimal(SIZE_ASK).multiply(SATOSHI_MULTIPLIER).longValue();
        bh.consume(bid);
        bh.consume(ask);
        bh.consume(bidSz);
        bh.consume(askSz);
    }

    /**
     * Stage 3: Construct a new Quote object with all fields set.
     */
    @Benchmark
    public void quoteConstructionNew(Blackhole bh) {
        Quote quote = new Quote(symbol, 6743215000000L, 6743216000000L,
                123400000L, 456700000L, System.nanoTime());
        quote.setReceivedAt(System.nanoTime());
        quote.setPriceScale(100_000_000);
        bh.consume(quote);
    }

    /**
     * Stage 3 (pooled): Construct a Quote using ObjectPool for zero-allocation reuse.
     */
    @Benchmark
    public void quoteConstructionPooled(Blackhole bh) {
        Quote quote = quotePool.acquire();
        quote.setSymbol(symbol);
        quote.setBidPrice(6743215000000L);
        quote.setAskPrice(6743216000000L);
        quote.setBidSize(123400000L);
        quote.setAskSize(456700000L);
        quote.setTimestamp(System.nanoTime());
        quote.setReceivedAt(System.nanoTime());
        quote.setPriceScale(100_000_000);
        bh.consume(quote);
        quotePool.release(quote);
    }

    /**
     * Stage 2 (optimized): Parse 4 price strings using FastDecimalParser.
     * Zero-allocation alternative to BigDecimal.
     */
    @Benchmark
    public void priceParsingFastDecimal(Blackhole bh) {
        long bid = FastDecimalParser.parseDecimal(PRICE_BID, 8);
        long ask = FastDecimalParser.parseDecimal(PRICE_ASK, 8);
        long bidSz = FastDecimalParser.parseDecimal(SIZE_BID, 8);
        long askSz = FastDecimalParser.parseDecimal(SIZE_ASK, 8);
        bh.consume(bid);
        bh.consume(ask);
        bh.consume(bidSz);
        bh.consume(askSz);
    }

    /**
     * Stage 5: Pre-trade risk check + ring buffer publish.
     * Creates a realistic order and submits it through the TradingEngine.
     * Resets daily counters each iteration to avoid hitting limits.
     */
    @Benchmark
    public void riskCheckPreTrade(Blackhole bh) {
        tradingEngine.resetDailyCounters();
        Order order = new Order();
        order.setSymbol(symbol);
        order.setSide(OrderSide.BUY);
        order.setType(OrderType.LIMIT);
        order.setPrice(6743215000000L);
        order.setQuantity(100000000L); // 1.0 BTC in satoshis
        order.setPriceScale(100_000_000);
        String rejection = tradingEngine.submitOrder(order);
        bh.consume(rejection);
    }

    /**
     * Stages 1-6 (BASELINE): Full pipeline using BigDecimal parsing.
     * JSON parse -> BigDecimal price extraction -> Quote construction -> ring buffer publish.
     */
    @Benchmark
    public void fullPipelineBaseline(Blackhole bh) throws Exception {
        // Stage 1: JSON parse
        JsonNode root = objectMapper.readTree(BINANCE_BOOK_TICKER_JSON);
        JsonNode data = root.get("data");

        // Stage 2: Price extraction (BigDecimal — old path)
        long bidPrice = new BigDecimal(data.get("b").asText()).multiply(SATOSHI_MULTIPLIER).longValue();
        long askPrice = new BigDecimal(data.get("a").asText()).multiply(SATOSHI_MULTIPLIER).longValue();
        long bidSize = new BigDecimal(data.get("B").asText()).multiply(SATOSHI_MULTIPLIER).longValue();
        long askSize = new BigDecimal(data.get("A").asText()).multiply(SATOSHI_MULTIPLIER).longValue();

        // Stage 3: Quote construction (new — old path)
        Quote quote = new Quote();
        quote.setSymbol(symbol);
        quote.setBidPrice(bidPrice);
        quote.setAskPrice(askPrice);
        quote.setBidSize(bidSize);
        quote.setAskSize(askSize);
        quote.setTimestamp(System.nanoTime());
        quote.setReceivedAt(System.nanoTime());
        quote.setPriceScale(100_000_000);

        // Stage 4-6: ring buffer publish
        tradingEngine.onQuoteUpdate(quote);

        bh.consume(quote);
    }

    /**
     * Stages 1-6 (OPTIMIZED): Full pipeline using FastDecimalParser + pooled Quote.
     * JSON parse -> FastDecimal price extraction -> pooled Quote -> ring buffer publish.
     */
    @Benchmark
    public void fullPipelineOptimized(Blackhole bh) throws Exception {
        // Stage 1: JSON parse
        JsonNode root = objectMapper.readTree(BINANCE_BOOK_TICKER_JSON);
        JsonNode data = root.get("data");

        // Stage 2: Price extraction (FastDecimalParser — new path)
        long bidPrice = FastDecimalParser.parseDecimal(data.get("b").asText(), 8);
        long askPrice = FastDecimalParser.parseDecimal(data.get("a").asText(), 8);
        long bidSize = FastDecimalParser.parseDecimal(data.get("B").asText(), 8);
        long askSize = FastDecimalParser.parseDecimal(data.get("A").asText(), 8);

        // Stage 3: Quote construction (pooled — new path)
        Quote quote = quotePool.acquire();
        quote.setSymbol(symbol);
        quote.setBidPrice(bidPrice);
        quote.setAskPrice(askPrice);
        quote.setBidSize(bidSize);
        quote.setAskSize(askSize);
        quote.setTimestamp(System.nanoTime());
        quote.setReceivedAt(System.nanoTime());
        quote.setPriceScale(100_000_000);

        // Stage 4-6: ring buffer publish
        tradingEngine.onQuoteUpdate(quote);

        bh.consume(quote);
        quotePool.release(quote);
    }

    // ==================== Parser Strategy Comparison Benchmarks ====================

    /**
     * Parse bookTicker using ManualBinanceParser (Option C — zero-library byte scanner).
     * This is the fastest option and the default.
     */
    @Benchmark
    public void parseTickerManual(Blackhole bh) {
        BinanceMessageParser.TickerFields fields = manualParser.parseTicker(BINANCE_BOOK_TICKER_JSON);
        bh.consume(fields);
    }

    /**
     * Parse bookTicker using StreamingBinanceParser (Jackson pull-parser, no tree).
     */
    @Benchmark
    public void parseTickerStreaming(Blackhole bh) {
        BinanceMessageParser.TickerFields fields = streamingParser.parseTicker(BINANCE_BOOK_TICKER_JSON);
        bh.consume(fields);
    }

    /**
     * Parse bookTicker using JacksonBinanceParser (baseline — full Jackson tree).
     */
    @Benchmark
    public void parseTickerJackson(Blackhole bh) {
        BinanceMessageParser.TickerFields fields = jacksonParser.parseTicker(BINANCE_BOOK_TICKER_JSON);
        bh.consume(fields);
    }

    /**
     * Full pipeline with ManualBinanceParser: raw string → manual parse → pooled Quote → ring buffer.
     * Eliminates both Jackson tree AND BigDecimal allocations.
     */
    @Benchmark
    public void fullPipelineManualParser(Blackhole bh) {
        BinanceMessageParser.TickerFields fields = manualParser.parseTicker(BINANCE_BOOK_TICKER_JSON);
        if (fields == null) return;

        Quote quote = quotePool.acquire();
        quote.setSymbol(symbol);
        quote.setBidPrice(fields.bidPrice());
        quote.setAskPrice(fields.askPrice());
        quote.setBidSize(fields.bidSize());
        quote.setAskSize(fields.askSize());
        quote.setTimestamp(System.nanoTime());
        quote.setReceivedAt(System.nanoTime());
        quote.setPriceScale(100_000_000);

        tradingEngine.onQuoteUpdate(quote);

        bh.consume(quote);
        quotePool.release(quote);
    }

    /**
     * Full pipeline with StreamingBinanceParser: raw string → Jackson streaming parse → pooled Quote → ring buffer.
     */
    @Benchmark
    public void fullPipelineStreamingParser(Blackhole bh) {
        BinanceMessageParser.TickerFields fields = streamingParser.parseTicker(BINANCE_BOOK_TICKER_JSON);
        if (fields == null) return;

        Quote quote = quotePool.acquire();
        quote.setSymbol(symbol);
        quote.setBidPrice(fields.bidPrice());
        quote.setAskPrice(fields.askPrice());
        quote.setBidSize(fields.bidSize());
        quote.setAskSize(fields.askSize());
        quote.setTimestamp(System.nanoTime());
        quote.setReceivedAt(System.nanoTime());
        quote.setPriceScale(100_000_000);

        tradingEngine.onQuoteUpdate(quote);

        bh.consume(quote);
        quotePool.release(quote);
    }
}
