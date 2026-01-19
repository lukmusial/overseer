package com.hft.core.model;

/**
 * Market quote with bid/ask prices and sizes.
 * Mutable for object pooling - use reset() and set methods.
 */
public class Quote implements Poolable {
    private Symbol symbol;
    private long bidPrice;      // Price in minor units (e.g., cents)
    private long askPrice;      // Price in minor units
    private long bidSize;       // Size in base units
    private long askSize;       // Size in base units
    private long timestamp;     // Epoch nanos from exchange
    private long receivedAt;    // Local receive time (nanos)
    private long sequenceNumber;

    // Price scale factor (e.g., 100 for 2 decimal places)
    private int priceScale = 100;

    public Quote() {
        // Default constructor for object pooling
    }

    public Quote(Symbol symbol, long bidPrice, long askPrice, long bidSize, long askSize, long timestamp) {
        this.symbol = symbol;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.bidSize = bidSize;
        this.askSize = askSize;
        this.timestamp = timestamp;
    }

    public void reset() {
        symbol = null;
        bidPrice = 0;
        askPrice = 0;
        bidSize = 0;
        askSize = 0;
        timestamp = 0;
        receivedAt = 0;
        sequenceNumber = 0;
    }

    public void copyFrom(Quote other) {
        this.symbol = other.symbol;
        this.bidPrice = other.bidPrice;
        this.askPrice = other.askPrice;
        this.bidSize = other.bidSize;
        this.askSize = other.askSize;
        this.timestamp = other.timestamp;
        this.receivedAt = other.receivedAt;
        this.sequenceNumber = other.sequenceNumber;
        this.priceScale = other.priceScale;
    }

    // Getters and setters
    public Symbol getSymbol() {
        return symbol;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public long getBidPrice() {
        return bidPrice;
    }

    public void setBidPrice(long bidPrice) {
        this.bidPrice = bidPrice;
    }

    public long getAskPrice() {
        return askPrice;
    }

    public void setAskPrice(long askPrice) {
        this.askPrice = askPrice;
    }

    public long getBidSize() {
        return bidSize;
    }

    public void setBidSize(long bidSize) {
        this.bidSize = bidSize;
    }

    public long getAskSize() {
        return askSize;
    }

    public void setAskSize(long askSize) {
        this.askSize = askSize;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public int getPriceScale() {
        return priceScale;
    }

    public void setPriceScale(int priceScale) {
        this.priceScale = priceScale;
    }

    /**
     * Returns mid price = (bid + ask) / 2
     */
    public long getMidPrice() {
        return (bidPrice + askPrice) / 2;
    }

    /**
     * Returns spread = ask - bid
     */
    public long getSpread() {
        return askPrice - bidPrice;
    }

    /**
     * Returns bid price as double with proper scaling
     */
    public double getBidPriceAsDouble() {
        return (double) bidPrice / priceScale;
    }

    /**
     * Returns ask price as double with proper scaling
     */
    public double getAskPriceAsDouble() {
        return (double) askPrice / priceScale;
    }

    @Override
    public String toString() {
        return String.format("Quote{%s bid=%.4f@%d ask=%.4f@%d ts=%d}",
                symbol, getBidPriceAsDouble(), bidSize, getAskPriceAsDouble(), askSize, timestamp);
    }
}
