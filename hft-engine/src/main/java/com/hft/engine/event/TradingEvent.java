package com.hft.engine.event;

import com.hft.core.model.*;

/**
 * Main event type for the trading engine disruptor.
 * Mutable and reusable to avoid allocations.
 */
public class TradingEvent {
    private EventType type;
    private long sequenceId;
    private long timestampNanos;

    // Order data
    private long clientOrderId;
    private String exchangeOrderId;
    private Symbol symbol;
    private OrderSide side;
    private OrderType orderType;
    private OrderStatus status;
    private TimeInForce timeInForce;
    private long price;
    private long stopPrice;
    private long quantity;
    private long filledQuantity;
    private long filledPrice;
    private String rejectReason;

    // Quote data
    private long bidPrice;
    private long askPrice;
    private long bidSize;
    private long askSize;

    // Trade/Fill data
    private long tradeId;
    private long commission;

    // Price scaling
    private int priceScale = 100;

    // Strategy reference
    private String strategyId;

    public void reset() {
        type = null;
        sequenceId = 0;
        timestampNanos = 0;
        clientOrderId = 0;
        exchangeOrderId = null;
        symbol = null;
        side = null;
        orderType = null;
        status = null;
        timeInForce = null;
        price = 0;
        stopPrice = 0;
        quantity = 0;
        filledQuantity = 0;
        filledPrice = 0;
        rejectReason = null;
        bidPrice = 0;
        askPrice = 0;
        bidSize = 0;
        askSize = 0;
        tradeId = 0;
        commission = 0;
        priceScale = 100;
        strategyId = null;
    }

    public void copyFrom(TradingEvent other) {
        this.type = other.type;
        this.sequenceId = other.sequenceId;
        this.timestampNanos = other.timestampNanos;
        this.clientOrderId = other.clientOrderId;
        this.exchangeOrderId = other.exchangeOrderId;
        this.symbol = other.symbol;
        this.side = other.side;
        this.orderType = other.orderType;
        this.status = other.status;
        this.timeInForce = other.timeInForce;
        this.price = other.price;
        this.stopPrice = other.stopPrice;
        this.quantity = other.quantity;
        this.filledQuantity = other.filledQuantity;
        this.filledPrice = other.filledPrice;
        this.rejectReason = other.rejectReason;
        this.bidPrice = other.bidPrice;
        this.askPrice = other.askPrice;
        this.bidSize = other.bidSize;
        this.askSize = other.askSize;
        this.tradeId = other.tradeId;
        this.commission = other.commission;
        this.priceScale = other.priceScale;
        this.strategyId = other.strategyId;
    }

    // Populate methods for different event types
    public void populateNewOrder(Order order) {
        this.type = EventType.NEW_ORDER;
        this.timestampNanos = System.nanoTime();
        this.clientOrderId = order.getClientOrderId();
        this.symbol = order.getSymbol();
        this.side = order.getSide();
        this.orderType = order.getType();
        this.timeInForce = order.getTimeInForce();
        this.price = order.getPrice();
        this.stopPrice = order.getStopPrice();
        this.quantity = order.getQuantity();
        this.priceScale = order.getPriceScale();
        this.strategyId = order.getStrategyId();
    }

    public void populateOrderAccepted(Order order) {
        this.type = EventType.ORDER_ACCEPTED;
        this.timestampNanos = System.nanoTime();
        this.clientOrderId = order.getClientOrderId();
        this.exchangeOrderId = order.getExchangeOrderId();
        this.symbol = order.getSymbol();
        this.status = OrderStatus.ACCEPTED;
    }

    public void populateOrderRejected(Order order, String reason) {
        this.type = EventType.ORDER_REJECTED;
        this.timestampNanos = System.nanoTime();
        this.clientOrderId = order.getClientOrderId();
        this.symbol = order.getSymbol();
        this.status = OrderStatus.REJECTED;
        this.rejectReason = reason;
    }

    public void populateOrderFilled(Order order, long fillQty, long fillPrice) {
        this.type = EventType.ORDER_FILLED;
        this.timestampNanos = System.nanoTime();
        this.clientOrderId = order.getClientOrderId();
        this.exchangeOrderId = order.getExchangeOrderId();
        this.symbol = order.getSymbol();
        this.side = order.getSide();
        this.filledQuantity = fillQty;
        this.filledPrice = fillPrice;
        this.priceScale = order.getPriceScale();
        this.status = order.getFilledQuantity() + fillQty >= order.getQuantity()
                ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    public void populateOrderCancelled(Order order) {
        this.type = EventType.ORDER_CANCELLED;
        this.timestampNanos = System.nanoTime();
        this.clientOrderId = order.getClientOrderId();
        this.exchangeOrderId = order.getExchangeOrderId();
        this.symbol = order.getSymbol();
        this.status = OrderStatus.CANCELLED;
    }

    public void populateCancelOrder(long clientOrderId, Symbol symbol) {
        this.type = EventType.CANCEL_ORDER;
        this.timestampNanos = System.nanoTime();
        this.clientOrderId = clientOrderId;
        this.symbol = symbol;
    }

    public void populateQuoteUpdate(Quote quote) {
        this.type = EventType.QUOTE_UPDATE;
        this.timestampNanos = System.nanoTime();
        this.symbol = quote.getSymbol();
        this.bidPrice = quote.getBidPrice();
        this.askPrice = quote.getAskPrice();
        this.bidSize = quote.getBidSize();
        this.askSize = quote.getAskSize();
        this.priceScale = quote.getPriceScale();
    }

    public void populateTradeUpdate(Trade trade) {
        this.type = EventType.TRADE_UPDATE;
        this.timestampNanos = System.nanoTime();
        this.symbol = trade.getSymbol();
        this.tradeId = trade.getTradeId();
        this.price = trade.getPrice();
        this.quantity = trade.getQuantity();
        this.side = trade.getSide();
    }

    // Getters and setters
    public EventType getType() { return type; }
    public void setType(EventType type) { this.type = type; }

    public long getSequenceId() { return sequenceId; }
    public void setSequenceId(long sequenceId) { this.sequenceId = sequenceId; }

    public long getTimestampNanos() { return timestampNanos; }
    public void setTimestampNanos(long timestampNanos) { this.timestampNanos = timestampNanos; }

    public long getClientOrderId() { return clientOrderId; }
    public void setClientOrderId(long clientOrderId) { this.clientOrderId = clientOrderId; }

    public String getExchangeOrderId() { return exchangeOrderId; }
    public void setExchangeOrderId(String exchangeOrderId) { this.exchangeOrderId = exchangeOrderId; }

    public Symbol getSymbol() { return symbol; }
    public void setSymbol(Symbol symbol) { this.symbol = symbol; }

    public OrderSide getSide() { return side; }
    public void setSide(OrderSide side) { this.side = side; }

    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public TimeInForce getTimeInForce() { return timeInForce; }
    public void setTimeInForce(TimeInForce timeInForce) { this.timeInForce = timeInForce; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public long getStopPrice() { return stopPrice; }
    public void setStopPrice(long stopPrice) { this.stopPrice = stopPrice; }

    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }

    public long getFilledQuantity() { return filledQuantity; }
    public void setFilledQuantity(long filledQuantity) { this.filledQuantity = filledQuantity; }

    public long getFilledPrice() { return filledPrice; }
    public void setFilledPrice(long filledPrice) { this.filledPrice = filledPrice; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public long getBidPrice() { return bidPrice; }
    public void setBidPrice(long bidPrice) { this.bidPrice = bidPrice; }

    public long getAskPrice() { return askPrice; }
    public void setAskPrice(long askPrice) { this.askPrice = askPrice; }

    public long getBidSize() { return bidSize; }
    public void setBidSize(long bidSize) { this.bidSize = bidSize; }

    public long getAskSize() { return askSize; }
    public void setAskSize(long askSize) { this.askSize = askSize; }

    public long getTradeId() { return tradeId; }
    public void setTradeId(long tradeId) { this.tradeId = tradeId; }

    public long getCommission() { return commission; }
    public void setCommission(long commission) { this.commission = commission; }

    public int getPriceScale() { return priceScale; }
    public void setPriceScale(int priceScale) { this.priceScale = priceScale; }

    public String getStrategyId() { return strategyId; }
    public void setStrategyId(String strategyId) { this.strategyId = strategyId; }

    @Override
    public String toString() {
        return String.format("TradingEvent{type=%s, seq=%d, symbol=%s, orderId=%d}",
                type, sequenceId, symbol, clientOrderId);
    }
}
