package com.hft.core.port;

import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;
import com.hft.core.model.Trade;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Port interface for market data streaming and queries.
 */
public interface MarketDataPort {

    /**
     * Subscribes to quote updates for a symbol.
     *
     * @param symbol The symbol to subscribe to
     * @return Future that completes when subscribed
     */
    CompletableFuture<Void> subscribeQuotes(Symbol symbol);

    /**
     * Subscribes to quote updates for multiple symbols.
     *
     * @param symbols The symbols to subscribe to
     * @return Future that completes when subscribed
     */
    CompletableFuture<Void> subscribeQuotes(Set<Symbol> symbols);

    /**
     * Unsubscribes from quote updates for a symbol.
     *
     * @param symbol The symbol to unsubscribe from
     * @return Future that completes when unsubscribed
     */
    CompletableFuture<Void> unsubscribeQuotes(Symbol symbol);

    /**
     * Subscribes to trade updates for a symbol.
     *
     * @param symbol The symbol to subscribe to
     * @return Future that completes when subscribed
     */
    CompletableFuture<Void> subscribeTrades(Symbol symbol);

    /**
     * Subscribes to trade updates for multiple symbols.
     *
     * @param symbols The symbols to subscribe to
     * @return Future that completes when subscribed
     */
    CompletableFuture<Void> subscribeTrades(Set<Symbol> symbols);

    /**
     * Unsubscribes from trade updates for a symbol.
     *
     * @param symbol The symbol to unsubscribe from
     * @return Future that completes when unsubscribed
     */
    CompletableFuture<Void> unsubscribeTrades(Symbol symbol);

    /**
     * Gets the current quote for a symbol.
     *
     * @param symbol The symbol to get quote for
     * @return Future containing the current quote
     */
    CompletableFuture<Quote> getQuote(Symbol symbol);

    /**
     * Gets recent trades for a symbol.
     *
     * @param symbol The symbol to get trades for
     * @param limit Maximum number of trades to return
     * @return Future containing recent trades
     */
    CompletableFuture<List<Trade>> getRecentTrades(Symbol symbol, int limit);

    /**
     * Returns the set of currently subscribed symbols.
     */
    Set<Symbol> getSubscribedSymbols();

    /**
     * Registers a callback for quote updates.
     *
     * @param listener The callback to invoke on quote updates
     */
    void addQuoteListener(Consumer<Quote> listener);

    /**
     * Removes a quote update callback.
     *
     * @param listener The callback to remove
     */
    void removeQuoteListener(Consumer<Quote> listener);

    /**
     * Registers a callback for trade updates.
     *
     * @param listener The callback to invoke on trade updates
     */
    void addTradeListener(Consumer<Trade> listener);

    /**
     * Removes a trade update callback.
     *
     * @param listener The callback to remove
     */
    void removeTradeListener(Consumer<Trade> listener);

    /**
     * Returns market data statistics.
     */
    MarketDataStats getStats();

    /**
     * Market data statistics for monitoring.
     */
    record MarketDataStats(
            long quotesReceived,
            long tradesReceived,
            long avgQuoteLatencyNanos,
            long avgTradeLatencyNanos,
            long p99QuoteLatencyNanos,
            long p99TradeLatencyNanos,
            long staleQuoteCount,
            long outOfSequenceCount
    ) {
        public static MarketDataStats empty() {
            return new MarketDataStats(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
