package com.hft.core.model;

import java.util.Objects;

/**
 * Trading symbol/instrument identifier.
 * Immutable and cached for low-latency access.
 */
public final class Symbol {
    private final String ticker;
    private final Exchange exchange;
    private final AssetClass assetClass;
    private final int hashCode;

    public Symbol(String ticker, Exchange exchange) {
        this.ticker = Objects.requireNonNull(ticker, "ticker").toUpperCase();
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.assetClass = exchange.getAssetClass();
        this.hashCode = computeHashCode();
    }

    public String getTicker() {
        return ticker;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    /**
     * Returns the full symbol identifier in format "EXCHANGE:TICKER"
     */
    public String getFullSymbol() {
        return exchange.getId() + ":" + ticker;
    }

    private int computeHashCode() {
        return Objects.hash(ticker, exchange);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Symbol symbol)) return false;
        return ticker.equals(symbol.ticker) && exchange == symbol.exchange;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return getFullSymbol();
    }
}
