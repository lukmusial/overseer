package com.hft.exchange.alpaca;

import com.fasterxml.jackson.databind.JsonNode;
import com.hft.core.metrics.LatencyHistogram;
import com.hft.core.model.Exchange;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;
import com.hft.core.model.Trade;
import com.hft.core.port.MarketDataPort;
import com.hft.exchange.alpaca.dto.AlpacaQuote;
import com.hft.exchange.alpaca.dto.AlpacaTrade;
import com.hft.exchange.alpaca.parser.AlpacaMessageParser;
import com.hft.exchange.alpaca.parser.JacksonAlpacaParser;
import com.hft.exchange.alpaca.parser.ManualAlpacaParser;
import com.hft.exchange.alpaca.parser.StreamingAlpacaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hft.core.model.ObjectPool;
import com.hft.core.util.FastDecimalParser;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.hft.core.util.ListenerSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Alpaca implementation of MarketDataPort for real-time and historical market data.
 */
public class AlpacaMarketDataPort implements MarketDataPort {
    private static final Logger log = LoggerFactory.getLogger(AlpacaMarketDataPort.class);
    private static final int PRICE_SCALE = 100;

    private final AlpacaHttpClient httpClient;
    private final AlpacaWebSocketClient webSocketClient;
    private final Set<Symbol> subscribedSymbols = ConcurrentHashMap.newKeySet();
    private final ListenerSet<Quote> quoteListeners = new ListenerSet<>();
    private final ListenerSet<Trade> tradeListeners = new ListenerSet<>();
    private final ListenerSet.ExceptionSink logError =
            (listener, cause) -> log.error("Error in market data listener", cause);

    // Statistics
    private final AtomicLong quotesReceived = new AtomicLong();
    private final AtomicLong tradesReceived = new AtomicLong();
    private final AtomicLong staleQuoteCount = new AtomicLong();
    private final AtomicLong outOfSequenceCount = new AtomicLong();
    private final LatencyHistogram quoteLatency = new LatencyHistogram();
    private final LatencyHistogram tradeLatency = new LatencyHistogram();

    // Last known sequence numbers per symbol
    private final Map<String, Long> lastSequence = new ConcurrentHashMap<>();

    // Symbol cache to avoid per-quote Symbol allocation + hash computation
    private final Map<String, Symbol> symbolCache = new ConcurrentHashMap<>();

    // Quote pool to avoid per-quote heap allocation
    private final ObjectPool<Quote> quotePool = new ObjectPool<>(Quote::new, 256);

    // Configurable message parser strategy
    private final AlpacaMessageParser messageParser;

    /** Parser modes: MANUAL (fastest, default), STREAMING (Jackson pull-parser), JACKSON (legacy tree). */
    public enum ParserMode { MANUAL, STREAMING, JACKSON }

    public AlpacaMarketDataPort(AlpacaHttpClient httpClient, AlpacaWebSocketClient webSocketClient) {
        this(httpClient, webSocketClient, ParserMode.MANUAL);
    }

    public AlpacaMarketDataPort(AlpacaHttpClient httpClient, AlpacaWebSocketClient webSocketClient, ParserMode parserMode) {
        this.httpClient = httpClient;
        this.webSocketClient = webSocketClient;
        this.messageParser = switch (parserMode) {
            case MANUAL -> new ManualAlpacaParser();
            case STREAMING -> new StreamingAlpacaParser();
            case JACKSON -> new JacksonAlpacaParser();
        };

        if (parserMode == ParserMode.JACKSON) {
            // Legacy path: use Jackson JsonNode listeners
            webSocketClient.addQuoteListener(this::handleQuoteMessage);
            webSocketClient.addTradeListener(this::handleTradeMessage);
        } else {
            // Fast path: use raw string listeners (bypass Jackson tree in WebSocket client)
            webSocketClient.addRawQuoteListener(this::handleRawQuoteMessage);
            webSocketClient.addRawTradeListener(this::handleRawTradeMessage);
        }

        log.info("AlpacaMarketDataPort using {} parser", messageParser.name());
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

        return webSocketClient.subscribeQuotes(tickers)
                .thenRun(() -> subscribedSymbols.addAll(symbols));
    }

    @Override
    public CompletableFuture<Void> unsubscribeQuotes(Symbol symbol) {
        return webSocketClient.unsubscribeQuotes(List.of(symbol.getTicker()))
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
        String path = "/v2/stocks/" + symbol.getTicker() + "/quotes/latest";
        return httpClient.getMarketData(path, LatestQuoteResponse.class)
                .thenApply(response -> convertQuote(symbol, response.quote));
    }

    @Override
    public CompletableFuture<List<Trade>> getRecentTrades(Symbol symbol, int limit) {
        String path = "/v2/stocks/" + symbol.getTicker() + "/trades?limit=" + limit;
        return httpClient.getMarketData(path, TradesResponse.class)
                .thenApply(response -> {
                    if (response.trades == null) {
                        return List.of();
                    }
                    return Arrays.stream(response.trades)
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

    private void handleQuoteMessage(JsonNode node) {
        long receiveTime = System.nanoTime();
        quotesReceived.incrementAndGet();

        try {
            String ticker = node.path("S").asText();
            Symbol symbol = symbolCache.computeIfAbsent(ticker, t -> new Symbol(t, Exchange.ALPACA));

            Quote quote = quotePool.acquire();
            quote.setSymbol(symbol);
            quote.setBidPrice(parsePrice(node.path("bp").asText()));
            quote.setAskPrice(parsePrice(node.path("ap").asText()));
            quote.setBidSize(node.path("bs").asLong());
            quote.setAskSize(node.path("as").asLong());

            // Parse timestamp
            String timestamp = node.path("t").asText();
            if (timestamp != null && !timestamp.isEmpty()) {
                Instant instant = Instant.parse(timestamp);
                quote.setTimestamp(instant.toEpochMilli() * 1_000_000); // Convert to nanos
            }

            quote.setReceivedAt(receiveTime);

            // Track latency
            if (quote.getTimestamp() > 0) {
                long latency = receiveTime - quote.getTimestamp();
                quoteLatency.record(latency);

                // Check for stale quotes (> 1 second old)
                if (latency > 1_000_000_000L) {
                    staleQuoteCount.incrementAndGet();
                }
            }

            notifyQuoteListeners(quote);
            quotePool.release(quote);
        } catch (Exception e) {
            log.error("Error processing quote message", e);
        }
    }

    private void handleTradeMessage(JsonNode node) {
        long receiveTime = System.nanoTime();
        tradesReceived.incrementAndGet();

        try {
            String ticker = node.path("S").asText();
            Symbol symbol = symbolCache.computeIfAbsent(ticker, t -> new Symbol(t, Exchange.ALPACA));

            Trade trade = new Trade();
            trade.setSymbol(symbol);
            trade.setPrice(parsePrice(node.path("p").asText()));
            trade.setQuantity(node.path("s").asLong());

            // Parse timestamp
            String timestamp = node.path("t").asText();
            if (timestamp != null && !timestamp.isEmpty()) {
                Instant instant = Instant.parse(timestamp);
                trade.setExecutedAt(instant.toEpochMilli() * 1_000_000); // Convert to nanos
            }

            // Check sequence
            long tradeId = node.path("i").asLong();
            Long lastId = lastSequence.get(ticker);
            if (lastId != null && tradeId <= lastId) {
                outOfSequenceCount.incrementAndGet();
            }
            lastSequence.put(ticker, tradeId);

            // Track latency
            if (trade.getExecutedAt() > 0) {
                long latency = receiveTime - trade.getExecutedAt();
                tradeLatency.record(latency);
            }

            notifyTradeListeners(trade);
        } catch (Exception e) {
            log.error("Error processing trade message", e);
        }
    }

    /**
     * Fast-path handler for raw quote messages (bypasses Jackson tree entirely).
     */
    private void handleRawQuoteMessage(String rawJson) {
        long receiveTime = System.nanoTime();
        quotesReceived.incrementAndGet();

        try {
            AlpacaMessageParser.QuoteFields fields = messageParser.parseQuote(rawJson);
            if (fields == null) {
                log.warn("Failed to parse raw quote message");
                return;
            }

            Symbol symbol = symbolCache.computeIfAbsent(fields.symbol(), t -> new Symbol(t, Exchange.ALPACA));

            Quote quote = quotePool.acquire();
            quote.setSymbol(symbol);
            quote.setBidPrice(fields.bidPrice());
            quote.setAskPrice(fields.askPrice());
            quote.setBidSize(fields.bidSize());
            quote.setAskSize(fields.askSize());

            if (fields.timestamp() != null && !fields.timestamp().isEmpty()) {
                Instant instant = Instant.parse(fields.timestamp());
                quote.setTimestamp(instant.toEpochMilli() * 1_000_000);
            }

            quote.setReceivedAt(receiveTime);

            if (quote.getTimestamp() > 0) {
                long latency = receiveTime - quote.getTimestamp();
                quoteLatency.record(latency);
                if (latency > 1_000_000_000L) {
                    staleQuoteCount.incrementAndGet();
                }
            }

            notifyQuoteListeners(quote);
            quotePool.release(quote);
        } catch (Exception e) {
            log.error("Error processing raw quote message", e);
        }
    }

    /**
     * Fast-path handler for raw trade messages.
     */
    private void handleRawTradeMessage(String rawJson) {
        long receiveTime = System.nanoTime();
        tradesReceived.incrementAndGet();

        try {
            AlpacaMessageParser.TradeFields fields = messageParser.parseTrade(rawJson);
            if (fields == null) {
                log.warn("Failed to parse raw trade message");
                return;
            }

            String ticker = fields.symbol();
            Symbol symbol = symbolCache.computeIfAbsent(ticker, t -> new Symbol(t, Exchange.ALPACA));

            Trade trade = new Trade();
            trade.setSymbol(symbol);
            trade.setPrice(fields.price());
            trade.setQuantity(fields.quantity());

            if (fields.timestamp() != null && !fields.timestamp().isEmpty()) {
                Instant instant = Instant.parse(fields.timestamp());
                trade.setExecutedAt(instant.toEpochMilli() * 1_000_000);
            }

            long tradeId = fields.tradeId();
            Long lastId = lastSequence.get(ticker);
            if (lastId != null && tradeId <= lastId) {
                outOfSequenceCount.incrementAndGet();
            }
            lastSequence.put(ticker, tradeId);

            if (trade.getExecutedAt() > 0) {
                long latency = receiveTime - trade.getExecutedAt();
                tradeLatency.record(latency);
            }

            notifyTradeListeners(trade);
        } catch (Exception e) {
            log.error("Error processing raw trade message", e);
        }
    }

    private Quote convertQuote(Symbol symbol, AlpacaQuote alpacaQuote) {
        Quote quote = new Quote();
        quote.setSymbol(symbol);
        quote.setBidPrice(parsePrice(alpacaQuote.getBp()));
        quote.setAskPrice(parsePrice(alpacaQuote.getAp()));
        quote.setBidSize(alpacaQuote.getBs());
        quote.setAskSize(alpacaQuote.getAs());

        if (alpacaQuote.getT() != null) {
            quote.setTimestamp(alpacaQuote.getT().toEpochMilli() * 1_000_000);
        }
        quote.setReceivedAt(System.nanoTime());

        return quote;
    }

    private Trade convertTrade(Symbol symbol, AlpacaTrade alpacaTrade) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setPrice(parsePrice(alpacaTrade.getP()));
        trade.setQuantity(alpacaTrade.getS());

        if (alpacaTrade.getT() != null) {
            trade.setExecutedAt(alpacaTrade.getT().toEpochMilli() * 1_000_000);
        }

        return trade;
    }

    private long parsePrice(String price) {
        return FastDecimalParser.parseDecimal(price, 2, 0);
    }

    private void notifyQuoteListeners(Quote quote) {
        quoteListeners.notify(quote, logError);
    }

    private void notifyTradeListeners(Trade trade) {
        tradeListeners.notify(trade, logError);
    }

    // Response DTOs for REST API
    private static class LatestQuoteResponse {
        public AlpacaQuote quote;
    }

    private static class TradesResponse {
        public AlpacaTrade[] trades;
    }
}
