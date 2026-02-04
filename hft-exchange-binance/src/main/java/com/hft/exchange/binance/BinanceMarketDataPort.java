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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final List<Consumer<Quote>> quoteListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Trade>> tradeListeners = new CopyOnWriteArrayList<>();

    // Statistics
    private final AtomicLong quotesReceived = new AtomicLong();
    private final AtomicLong tradesReceived = new AtomicLong();
    private final AtomicLong staleQuoteCount = new AtomicLong();
    private final AtomicLong outOfSequenceCount = new AtomicLong();
    private final LatencyHistogram quoteLatency = new LatencyHistogram();
    private final LatencyHistogram tradeLatency = new LatencyHistogram();

    // Last known trade IDs per symbol
    private final Map<String, Long> lastTradeId = new ConcurrentHashMap<>();

    public BinanceMarketDataPort(BinanceHttpClient httpClient, BinanceWebSocketClient webSocketClient) {
        this.httpClient = httpClient;
        this.webSocketClient = webSocketClient;

        // Register for WebSocket callbacks
        webSocketClient.addTickerListener(this::handleTickerMessage);
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
            Symbol symbol = new Symbol(ticker, Exchange.BINANCE);

            Quote quote = new Quote();
            quote.setSymbol(symbol);
            quote.setBidPrice(parsePrice(node.path("b").asText()));
            quote.setAskPrice(parsePrice(node.path("a").asText()));
            quote.setBidSize(parseQuantity(node.path("B").asText()));
            quote.setAskSize(parseQuantity(node.path("A").asText()));

            quote.setPriceScale(PRICE_SCALE);

            // Binance bookTicker doesn't have timestamp, use local time
            quote.setTimestamp(receiveTime);
            quote.setReceivedAt(receiveTime);

            notifyQuoteListeners(quote);
        } catch (Exception e) {
            log.error("Error processing ticker message", e);
        }
    }

    private void handleTradeMessage(JsonNode node) {
        long receiveTime = System.nanoTime();
        tradesReceived.incrementAndGet();

        try {
            String ticker = node.path("s").asText();
            Symbol symbol = new Symbol(ticker, Exchange.BINANCE);

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

    private Quote convertTicker(Symbol symbol, BinanceTicker ticker) {
        Quote quote = new Quote();
        quote.setSymbol(symbol);
        quote.setBidPrice(parsePrice(ticker.getBidPrice()));
        quote.setAskPrice(parsePrice(ticker.getAskPrice()));
        quote.setBidSize(parseQuantity(ticker.getBidQty()));
        quote.setAskSize(parseQuantity(ticker.getAskQty()));
        quote.setTimestamp(System.nanoTime());
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
        if (price == null || price.isBlank()) return 0;
        BigDecimal bd = new BigDecimal(price);
        return bd.multiply(BigDecimal.valueOf(PRICE_SCALE)).longValue();
    }

    private long parseQuantity(String qty) {
        if (qty == null || qty.isBlank()) return 0;
        BigDecimal bd = new BigDecimal(qty);
        // Store quantity with 8 decimal precision
        return bd.multiply(BigDecimal.valueOf(100_000_000)).longValue();
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
}
