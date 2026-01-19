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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final List<Consumer<Quote>> quoteListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Trade>> tradeListeners = new CopyOnWriteArrayList<>();

    // Statistics
    private final AtomicLong quotesReceived = new AtomicLong();
    private final AtomicLong tradesReceived = new AtomicLong();
    private final AtomicLong staleQuoteCount = new AtomicLong();
    private final AtomicLong outOfSequenceCount = new AtomicLong();
    private final LatencyHistogram quoteLatency = new LatencyHistogram();
    private final LatencyHistogram tradeLatency = new LatencyHistogram();

    // Last known sequence numbers per symbol
    private final Map<String, Long> lastSequence = new ConcurrentHashMap<>();

    public AlpacaMarketDataPort(AlpacaHttpClient httpClient, AlpacaWebSocketClient webSocketClient) {
        this.httpClient = httpClient;
        this.webSocketClient = webSocketClient;

        // Register for WebSocket callbacks
        webSocketClient.addQuoteListener(this::handleQuoteMessage);
        webSocketClient.addTradeListener(this::handleTradeMessage);
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
            Symbol symbol = new Symbol(ticker, Exchange.ALPACA);

            Quote quote = new Quote();
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
        } catch (Exception e) {
            log.error("Error processing quote message", e);
        }
    }

    private void handleTradeMessage(JsonNode node) {
        long receiveTime = System.nanoTime();
        tradesReceived.incrementAndGet();

        try {
            String ticker = node.path("S").asText();
            Symbol symbol = new Symbol(ticker, Exchange.ALPACA);

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
        if (price == null || price.isBlank()) return 0;
        BigDecimal bd = new BigDecimal(price);
        return bd.multiply(BigDecimal.valueOf(PRICE_SCALE)).longValue();
    }

    private void notifyQuoteListeners(Quote quote) {
        for (Consumer<Quote> listener : quoteListeners) {
            try {
                listener.accept(quote);
            } catch (Exception e) {
                log.error("Error in quote listener", e);
            }
        }
    }

    private void notifyTradeListeners(Trade trade) {
        for (Consumer<Trade> listener : tradeListeners) {
            try {
                listener.accept(trade);
            } catch (Exception e) {
                log.error("Error in trade listener", e);
            }
        }
    }

    // Response DTOs for REST API
    private static class LatestQuoteResponse {
        public AlpacaQuote quote;
    }

    private static class TradesResponse {
        public AlpacaTrade[] trades;
    }
}
