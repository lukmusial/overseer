package com.hft.core.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Executed trade/fill representation.
 * Mutable for object pooling - use reset() between uses.
 */
public class Trade {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(System.nanoTime());

    private long tradeId;
    private String exchangeTradeId;

    // Order reference
    private long clientOrderId;
    private String exchangeOrderId;

    // Trade details
    private Symbol symbol;
    private OrderSide side;
    private long price;         // Price in minor units
    private long quantity;      // Executed quantity
    private int priceScale = 100;

    // Fees
    private long commission;
    private String commissionAsset;

    // Timestamps
    private long executedAt;    // Exchange execution time (epoch nanos)
    private long receivedAt;    // Local receive time (epoch nanos)

    // Liquidity indicator
    private boolean isMaker;

    public Trade() {
        this.tradeId = ID_GENERATOR.incrementAndGet();
        this.receivedAt = System.nanoTime();
    }

    public void reset() {
        tradeId = ID_GENERATOR.incrementAndGet();
        exchangeTradeId = null;
        clientOrderId = 0;
        exchangeOrderId = null;
        symbol = null;
        side = null;
        price = 0;
        quantity = 0;
        commission = 0;
        commissionAsset = null;
        executedAt = 0;
        receivedAt = System.nanoTime();
        isMaker = false;
    }

    public void copyFrom(Trade other) {
        this.tradeId = other.tradeId;
        this.exchangeTradeId = other.exchangeTradeId;
        this.clientOrderId = other.clientOrderId;
        this.exchangeOrderId = other.exchangeOrderId;
        this.symbol = other.symbol;
        this.side = other.side;
        this.price = other.price;
        this.quantity = other.quantity;
        this.priceScale = other.priceScale;
        this.commission = other.commission;
        this.commissionAsset = other.commissionAsset;
        this.executedAt = other.executedAt;
        this.receivedAt = other.receivedAt;
        this.isMaker = other.isMaker;
    }

    /**
     * Returns the notional value = price * quantity
     */
    public long getNotionalValue() {
        return price * quantity / priceScale;
    }

    // Getters and setters
    public long getTradeId() {
        return tradeId;
    }

    public String getExchangeTradeId() {
        return exchangeTradeId;
    }

    public void setExchangeTradeId(String exchangeTradeId) {
        this.exchangeTradeId = exchangeTradeId;
    }

    public long getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(long clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public String getExchangeOrderId() {
        return exchangeOrderId;
    }

    public void setExchangeOrderId(String exchangeOrderId) {
        this.exchangeOrderId = exchangeOrderId;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public int getPriceScale() {
        return priceScale;
    }

    public void setPriceScale(int priceScale) {
        this.priceScale = priceScale;
    }

    public long getCommission() {
        return commission;
    }

    public void setCommission(long commission) {
        this.commission = commission;
    }

    public String getCommissionAsset() {
        return commissionAsset;
    }

    public void setCommissionAsset(String commissionAsset) {
        this.commissionAsset = commissionAsset;
    }

    public long getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(long executedAt) {
        this.executedAt = executedAt;
    }

    public long getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    public boolean isMaker() {
        return isMaker;
    }

    public void setMaker(boolean maker) {
        isMaker = maker;
    }

    public double getPriceAsDouble() {
        return (double) price / priceScale;
    }

    @Override
    public String toString() {
        return String.format("Trade{id=%d, exId=%s, orderId=%d, %s %s %.4f x %d, maker=%b}",
                tradeId, exchangeTradeId, clientOrderId, symbol, side,
                getPriceAsDouble(), quantity, isMaker);
    }
}
