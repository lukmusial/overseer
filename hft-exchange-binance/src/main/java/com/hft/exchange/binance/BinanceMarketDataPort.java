package com.hft.exchange.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.hft.core.metrics.LatencyHistogram;
import com.hft.core.model.Exchange;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;
import com.hft.core.model.Trade;
import com.hft.core.port.MarketDataPort;
import com.hft.exchange.binance.dto.BinanceTicker;
import com.hft.exchange.binance.dto.BinanceTrade;
import com.hft.exchange.binance.parser.BinanceMessageParser;
import com.hft.exchange.binance.parser.JacksonBinanceParser;
import com.hft.exchange.binance.parser.ManualBinanceParser;
import com.hft.exchange.binance.parser.StreamingBinanceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hft.core.model.ObjectPool;
import com.hft.core.util.FastDecimalParser;
import com.hft.core.util.ListenerSet;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Binance implementation of MarketDataPort for real-time and historical market data.
 */
public class BinanceMarketDataPort implements MarketDataPort {
    private static final Logger log = LoggerFactory.getLogger(BinanceMarketDataPort.class);
    // Binance uses 8 decimal places
    private static final int PRICE_SCALE = 100_000_000;

    private final BinanceHttpClient httpClient;
    private final BinanceWebSocketClient webSocketClient;
    private final Set<Symbol> subscribedSymbols = ConcurrentHashMap.newKeySet();
    private final ListenerSet<Quote> quoteListeners = new ListenerSet<>();
    private final ListenerSet<Trade> tradeListeners = new ListenerSet<>();

    // Statistics
    private final AtomicLong quotesReceived = new AtomicLong();
    private final AtomicLong tradesReceived = new AtomicLong();
    private final AtomicLong staleQuoteCount = new AtomicLong();
    private final AtomicLong outOfSequenceCount = new AtomicLong();
    private final LatencyHistogram quoteLatency = new LatencyHistogram();
    private final LatencyHistogram tradeLatency = new LatencyHistogram();

    // Last known trade IDs per symbol
    private final Map<String, Long> lastTradeId = new ConcurrentHashMap<>();

    // Symbol cache to avoid per-quote Symbol allocation + hash computation
    private final Map<String, Symbol> symbolCache = new ConcurrentHashMap<>();

    // Quote pool to avoid per-quote heap allocation
    private final ObjectPool<Quote> quotePool = new ObjectPool<>(Quote::new, 256);

    // Configurable message parser strategy
    private final BinanceMessageParser messageParser;

    /** Parser modes: MANUAL (fastest, default), STREAMING (Jackson pull-parser), JACKSON (legacy tree). */
    public enum ParserMode { MANUAL, STREAMING, JACKSON }

    public BinanceMarketDataPort(BinanceHttpClient httpClient, BinanceWebSocketClient webSocketClient) {
        this(httpClient, webSocketClient, ParserMode.MANUAL);
    }

    public BinanceMarketDataPort(BinanceHttpClient httpClient, BinanceWebSocketClient webSocketClient, ParserMode parserMode) {
        this.httpClient = httpClient;
        this.webSocketClient = webSocketClient;
        this.messageParser = switch (parserMode) {
            case MANUAL -> new ManualBinanceParser();
            case STREAMING -> new StreamingBinanceParser();
            case JACKSON -> new JacksonBinanceParser();
        };

        if (parserMode == ParserMode.JACKSON) {
            // Legacy path: use Jackson JsonNode listeners
            webSocketClient.addTickerListener(this::handleTickerMessage);
            webSocketClient.addTradeListener(this::handleTradeMessage);
        } else {
            // Fast path: use raw string listeners (bypass Jackson tree in WebSocket client)
            webSocketClient.addRawTickerListener(this::handleRawTickerMessage);
            webSocketClient.addRawTradeListener(this::handleRawTradeMessage);
        }

        log.info("BinanceMarketDataPort using {} parser", messageParser.name());
    }

    @Override
    public CompletableFuture<Void> subscribeQuotes(Symbol symbol) {
        return subscribeQuotes(Set.of(symbol));
    }

    @Override
    public CompletableFuture<Void> subscribeQuotes(Set<Symbol> symbols) {
        Set<String> tickers = symbols.stream()
                .map(Symbol::getTicker)
                .collect(Collectors.toSet());

        // Pre-populate symbolCache so handleRawTickerMessage avoids a
        // computeIfAbsent miss (allocation + map mutex) on the first tick per symbol.
        for (Symbol s : symbols) {
            symbolCache.putIfAbsent(s.getTicker(), s);
        }

        return webSocketClient.subscribeTickers(tickers)
                .thenRun(() -> subscribedSymbols.addAll(symbols));
    }

    @Override
    public CompletableFuture<Void> unsubscribeQuotes(Symbol symbol) {
        return webSocketClient.unsubscribeTickers(List.of(symbol.getTicker()))
                .thenRun(() -> subscribedSymbols.remove(symbol));
    }

    @Override
    public CompletableFuture<Void> subscribeTrades(Symbol symbol) {
        return subscribeTrades(Set.of(symbol));
    }

    @Override
    public CompletableFuture<Void> subscribeTrades(Set<Symbol> symbols) {
        Set<String> tickers = symbols.stream()
                .map(Symbol::getTicker)
                .collect(Collectors.toSet());

        // Pre-populate symbolCache for the trade hot path too.
        for (Symbol s : symbols) {
            symbolCache.putIfAbsent(s.getTicker(), s);
        }

        return webSocketClient.subscribeTrades(tickers)
                .thenRun(() -> subscribedSymbols.addAll(symbols));
    }

    @Override
    public CompletableFuture<Void> unsubscribeTrades(Symbol symbol) {
        return webSocketClient.unsubscribeTrades(List.of(symbol.getTicker()))
                .thenRun(() -> subscribedSymbols.remove(symbol));
    }

    @Override
    public CompletableFuture<Quote> getQuote(Symbol symbol) {
        String path = "/api/v3/ticker/bookTicker?symbol=" + symbol.getTicker();
        return httpClient.publicGet(path, BinanceTicker.class)
                .thenApply(ticker -> convertTicker(symbol, ticker));
    }

    @Override
    public CompletableFuture<List<Trade>> getRecentTrades(Symbol symbol, int limit) {
        String path = "/api/v3/trades?symbol=" + symbol.getTicker() + "&limit=" + limit;
        return httpClient.publicGet(path, BinanceTrade[].class)
                .thenApply(trades -> {
                    if (trades == null) {
                        return List.of();
                    }
                    return Arrays.stream(trades)
                            .map(t -> convertTrade(symbol, t))
                            .toList();
                });
    }

    @Override
    public Set<Symbol> getSubscribedSymbols() {
        return Collections.unmodifiableSet(subscribedSymbols);
    }

    @Override
    public void addQuoteListener(Consumer<Quote> listener) {
        quoteListeners.add(listener);
    }

    @Override
    public void removeQuoteListener(Consumer<Quote> listener) {
        quoteListeners.remove(listener);
    }

    @Override
    public void addTradeListener(Consumer<Trade> listener) {
        tradeListeners.add(listener);
    }

    private final ListenerSet.ExceptionSink logError =
            (listener, cause) -> log.error("Error in market data listener", cause);

    @Override
    public void removeTradeListener(Consumer<Trade> listener) {
        tradeListeners.remove(listener);
    }

    @Override
    public MarketDataStats getStats() {
        var quoteStats = quoteLatency.getStats();
        var tradeStats = tradeLatency.getStats();

        return new MarketDataStats(
                quotesReceived.get(),
                tradesReceived.get(),
                (long) quoteStats.mean(),
                (long) tradeStats.mean(),
                quoteStats.p99(),
                tradeStats.p99(),
                staleQuoteCount.get(),
                outOfSequenceCount.get()
        );
    }

    private void handleTickerMessage(JsonNode node) {
        long receiveTime = System.nanoTime();
        quotesReceived.incrementAndGet();

        try {
            String ticker = node.path("s").asText();
            Symbol symbol = symbolCache.computeIfAbsent(ticker, t -> new Symbol(t, Exchange.BINANCE));

            Quote quote = quotePool.acquire();
            quote.setSymbol(symbol);
            quote.setBidPrice(parsePrice(node.path("b").asText()));
            quote.setAskPrice(parsePrice(node.path("a").asText()));
            quote.setBidSize(parseQuantity(node.path("B").asText()));
            quote.setAskSize(parseQuantity(node.path("A").asText()));

            quote.setPriceScale(PRICE_SCALE);

            // Binance bookTicker doesn't include a timestamp field.
            // Use epoch-based time for timestamp (needed for chart alignment)
            // and nanoTime for receivedAt (latency tracking).
            quote.setTimestamp(System.currentTimeMillis() * 1_000_000L);
            quote.setReceivedAt(receiveTime);

            notifyQuoteListeners(quote);
            quotePool.release(quote);
        } catch (Exception e) {
            log.error("Error processing ticker message", e);
        }
    }

    private void handleTradeMessage(JsonNode node) {
        long receiveTime = System.nanoTime();
        tradesReceived.incrementAndGet();

        try {
            String ticker = node.path("s").asText();
            Symbol symbol = symbolCache.computeIfAbsent(ticker, t -> new Symbol(t, Exchange.BINANCE));

            Trade trade = new Trade();
            trade.setSymbol(symbol);
            trade.setPrice(parsePrice(node.path("p").asText()));
            trade.setQuantity(parseQuantity(node.path("q").asText()));

            // Trade timestamp in milliseconds
            long tradeTime = node.path("T").asLong();
            if (tradeTime > 0) {
                trade.setExecutedAt(tradeTime * 1_000_000); // Convert to nanos
            }

            // Check sequence (trade ID)
            long tradeId = node.path("t").asLong();
            Long lastId = lastTradeId.get(ticker);
            if (lastId != null && tradeId <= lastId) {
                outOfSequenceCount.incrementAndGet();
            }
            lastTradeId.put(ticker, tradeId);

            // Set maker side
            boolean isBuyerMaker = node.path("m").asBoolean();
            trade.setMaker(isBuyerMaker);

            // Track latency
            if (trade.getExecutedAt() > 0) {
                long latency = receiveTime - trade.getExecutedAt();
                tradeLatency.record(latency);

                // Check for stale (> 1 second old)
                if (latency > 1_000_000_000L) {
                    staleQuoteCount.incrementAndGet();
                }
            }

            notifyTradeListeners(trade);
        } catch (Exception e) {
            log.error("Error processing trade message", e);
        }
    }

    /**
     * Fast-path handler for raw ticker messages (bypasses Jackson tree entirely).
     * Uses the configured BinanceMessageParser strategy.
     */
    private void handleRawTickerMessage(String rawJson) {
        long receiveTime = System.nanoTime();
        quotesReceived.incrementAndGet();

        try {
            BinanceMessageParser.TickerFields fields = messageParser.parseTicker(rawJson);
            if (fields == null) {
                log.warn("Failed to parse raw ticker message");
                return;
            }

            Symbol symbol = symbolCache.computeIfAbsent(fields.symbol(), t -> new Symbol(t, Exchange.BINANCE));

            Quote quote = quotePool.acquire();
            quote.setSymbol(symbol);
            quote.setBidPrice(fields.bidPrice());
            quote.setAskPrice(fields.askPrice());
            quote.setBidSize(fields.bidSize());
            quote.setAskSize(fields.askSize());
            quote.setPriceScale(PRICE_SCALE);
            quote.setTimestamp(System.currentTimeMillis() * 1_000_000L);
            quote.setReceivedAt(receiveTime);

            notifyQuoteListeners(quote);
            quotePool.release(quote);
        } catch (Exception e) {
            log.error("Error processing raw ticker message", e);
        }
    }

    /**
     * Fast-path handler for raw trade messages.
     */
    private void handleRawTradeMessage(String rawJson) {
        long receiveTime = System.nanoTime();
        tradesReceived.incrementAndGet();

        try {
            BinanceMessageParser.TradeFields fields = messageParser.parseTrade(rawJson);
            if (fields == null) {
                log.warn("Failed to parse raw trade message");
                return;
            }

            String ticker = fields.symbol();
            Symbol symbol = symbolCache.computeIfAbsent(ticker, t -> new Symbol(t, Exchange.BINANCE));

            Trade trade = new Trade();
            trade.setSymbol(symbol);
            trade.setPrice(fields.price());
            trade.setQuantity(fields.quantity());

            if (fields.tradeTimeMs() > 0) {
                trade.setExecutedAt(fields.tradeTimeMs() * 1_000_000);
            }

            long tradeId = fields.tradeId();
            Long lastId = lastTradeId.get(ticker);
            if (lastId != null && tradeId <= lastId) {
                outOfSequenceCount.incrementAndGet();
            }
            lastTradeId.put(ticker, tradeId);

            trade.setMaker(fields.isBuyerMaker());

            if (trade.getExecutedAt() > 0) {
                long latency = receiveTime - trade.getExecutedAt();
                tradeLatency.record(latency);
                if (latency > 1_000_000_000L) {
                    staleQuoteCount.incrementAndGet();
                }
            }

            notifyTradeListeners(trade);
        } catch (Exception e) {
            log.error("Error processing raw trade message", e);
        }
    }

    private Quote convertTicker(Symbol symbol, BinanceTicker ticker) {
        Quote quote = new Quote();
        quote.setSymbol(symbol);
        quote.setBidPrice(parsePrice(ticker.getBidPrice()));
        quote.setAskPrice(parsePrice(ticker.getAskPrice()));
        quote.setBidSize(parseQuantity(ticker.getBidQty()));
        quote.setAskSize(parseQuantity(ticker.getAskQty()));
        quote.setTimestamp(System.currentTimeMillis() * 1_000_000L);
        quote.setReceivedAt(System.nanoTime());
        return quote;
    }

    private Trade convertTrade(Symbol symbol, BinanceTrade binanceTrade) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setPrice(parsePrice(binanceTrade.getPrice()));
        trade.setQuantity(parseQuantity(binanceTrade.getQty()));
        trade.setExecutedAt(binanceTrade.getTime() * 1_000_000); // Convert to nanos
        trade.setMaker(binanceTrade.isBuyerMaker());
        return trade;
    }

    private long parsePrice(String price) {
        return FastDecimalParser.parseDecimal(price, 8, 0);
    }

    private long parseQuantity(String qty) {
        return FastDecimalParser.parseDecimal(qty, 8, 0);
    }

    private void notifyQuoteListeners(Quote quote) {
        quoteListeners.notify(quote, logError);
    }

    private void notifyTradeListeners(Trade trade) {
        tradeListeners.notify(trade, logError);
    }
}
