package com.hft.core.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Order representation with support for object pooling.
 * Mutable for low-latency reuse - use reset() between uses.
 */
public class Order implements Poolable {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(System.nanoTime());

    // Identifiers
    private long clientOrderId;
    private String exchangeOrderId;

    // Order details
    private Symbol symbol;
    private OrderSide side;
    private OrderType type;
    private OrderStatus status;
    private TimeInForce timeInForce;

    // Prices in minor units (e.g., cents)
    private long price;
    private long stopPrice;
    private int priceScale = 100;

    // Quantities
    private long quantity;
    private long filledQuantity;
    private long remainingQuantity;

    // Execution details
    private long averageFilledPrice;
    private long lastFilledPrice;
    private long lastFilledQuantity;

    // Timestamps (epoch nanos)
    private long createdAt;
    private long submittedAt;
    private long acceptedAt;
    private long lastUpdatedAt;

    // Strategy reference
    private String strategyId;
    private String algorithmId;

    // Performance metrics
    private long submitLatencyNanos;
    private long ackLatencyNanos;
    private long fillLatencyNanos;

    public Order() {
        this.clientOrderId = ID_GENERATOR.incrementAndGet();
        this.status = OrderStatus.PENDING;
        this.createdAt = System.nanoTime();
    }

    public void reset() {
        clientOrderId = ID_GENERATOR.incrementAndGet();
        exchangeOrderId = null;
        symbol = null;
        side = null;
        type = null;
        status = OrderStatus.PENDING;
        timeInForce = null;
        price = 0;
        stopPrice = 0;
        quantity = 0;
        filledQuantity = 0;
        remainingQuantity = 0;
        averageFilledPrice = 0;
        lastFilledPrice = 0;
        lastFilledQuantity = 0;
        createdAt = System.nanoTime();
        submittedAt = 0;
        acceptedAt = 0;
        lastUpdatedAt = 0;
        strategyId = null;
        algorithmId = null;
        submitLatencyNanos = 0;
        ackLatencyNanos = 0;
        fillLatencyNanos = 0;
    }

    public void copyFrom(Order other) {
        this.clientOrderId = other.clientOrderId;
        this.exchangeOrderId = other.exchangeOrderId;
        this.symbol = other.symbol;
        this.side = other.side;
        this.type = other.type;
        this.status = other.status;
        this.timeInForce = other.timeInForce;
        this.price = other.price;
        this.stopPrice = other.stopPrice;
        this.priceScale = other.priceScale;
        this.quantity = other.quantity;
        this.filledQuantity = other.filledQuantity;
        this.remainingQuantity = other.remainingQuantity;
        this.averageFilledPrice = other.averageFilledPrice;
        this.lastFilledPrice = other.lastFilledPrice;
        this.lastFilledQuantity = other.lastFilledQuantity;
        this.createdAt = other.createdAt;
        this.submittedAt = other.submittedAt;
        this.acceptedAt = other.acceptedAt;
        this.lastUpdatedAt = other.lastUpdatedAt;
        this.strategyId = other.strategyId;
        this.algorithmId = other.algorithmId;
        this.submitLatencyNanos = other.submitLatencyNanos;
        this.ackLatencyNanos = other.ackLatencyNanos;
        this.fillLatencyNanos = other.fillLatencyNanos;
    }

    // Builder-style setters for fluent API
    public Order symbol(Symbol symbol) {
        this.symbol = symbol;
        return this;
    }

    public Order side(OrderSide side) {
        this.side = side;
        return this;
    }

    public Order type(OrderType type) {
        this.type = type;
        return this;
    }

    public Order timeInForce(TimeInForce timeInForce) {
        this.timeInForce = timeInForce;
        return this;
    }

    public Order price(long price) {
        this.price = price;
        return this;
    }

    public Order quantity(long quantity) {
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        return this;
    }

    public Order strategyId(String strategyId) {
        this.strategyId = strategyId;
        return this;
    }

    public Order algorithmId(String algorithmId) {
        this.algorithmId = algorithmId;
        return this;
    }

    // Status transitions
    public void markSubmitted() {
        this.status = OrderStatus.SUBMITTED;
        this.submittedAt = System.nanoTime();
        this.submitLatencyNanos = submittedAt - createdAt;
        this.lastUpdatedAt = submittedAt;
    }

    public void markAccepted(String exchangeOrderId) {
        this.exchangeOrderId = exchangeOrderId;
        this.status = OrderStatus.ACCEPTED;
        this.acceptedAt = System.nanoTime();
        this.ackLatencyNanos = acceptedAt - submittedAt;
        this.lastUpdatedAt = acceptedAt;
    }

    public void markPartiallyFilled(long filledQty, long fillPrice) {
        this.lastFilledQuantity = filledQty;
        this.lastFilledPrice = fillPrice;
        this.filledQuantity += filledQty;
        this.remainingQuantity = quantity - filledQuantity;

        // Update average price
        if (averageFilledPrice == 0) {
            averageFilledPrice = fillPrice;
        } else {
            averageFilledPrice = ((averageFilledPrice * (filledQuantity - filledQty)) + (fillPrice * filledQty)) / filledQuantity;
        }

        this.status = OrderStatus.PARTIALLY_FILLED;
        this.lastUpdatedAt = System.nanoTime();
    }

    public void markFilled(long filledQty, long fillPrice) {
        markPartiallyFilled(filledQty, fillPrice);
        this.status = OrderStatus.FILLED;
        this.fillLatencyNanos = lastUpdatedAt - submittedAt;
    }

    public void markCancelled() {
        this.status = OrderStatus.CANCELLED;
        this.lastUpdatedAt = System.nanoTime();
    }

    public void markRejected() {
        this.status = OrderStatus.REJECTED;
        this.lastUpdatedAt = System.nanoTime();
    }

    // Getters
    public long getClientOrderId() {
        return clientOrderId;
    }

    public String getExchangeOrderId() {
        return exchangeOrderId;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public TimeInForce getTimeInForce() {
        return timeInForce;
    }

    public long getPrice() {
        return price;
    }

    public long getStopPrice() {
        return stopPrice;
    }

    public int getPriceScale() {
        return priceScale;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getFilledQuantity() {
        return filledQuantity;
    }

    public long getRemainingQuantity() {
        return remainingQuantity;
    }

    public long getAverageFilledPrice() {
        return averageFilledPrice;
    }

    public long getLastFilledPrice() {
        return lastFilledPrice;
    }

    public long getLastFilledQuantity() {
        return lastFilledQuantity;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public long getAcceptedAt() {
        return acceptedAt;
    }

    public long getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public String getAlgorithmId() {
        return algorithmId;
    }

    public long getSubmitLatencyNanos() {
        return submitLatencyNanos;
    }

    public long getAckLatencyNanos() {
        return ackLatencyNanos;
    }

    public long getFillLatencyNanos() {
        return fillLatencyNanos;
    }

    // Setters
    public void setExchangeOrderId(String exchangeOrderId) {
        this.exchangeOrderId = exchangeOrderId;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setTimeInForce(TimeInForce timeInForce) {
        this.timeInForce = timeInForce;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public void setStopPrice(long stopPrice) {
        this.stopPrice = stopPrice;
    }

    public void setPriceScale(int priceScale) {
        this.priceScale = priceScale;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
        this.remainingQuantity = quantity;
    }

    public double getPriceAsDouble() {
        return (double) price / priceScale;
    }

    public double getAverageFilledPriceAsDouble() {
        return (double) averageFilledPrice / priceScale;
    }

    @Override
    public String toString() {
        return String.format("Order{id=%d, exId=%s, %s %s %s %.4f x %d, status=%s, filled=%d}",
                clientOrderId, exchangeOrderId, symbol, side, type,
                getPriceAsDouble(), quantity, status, filledQuantity);
    }
}
