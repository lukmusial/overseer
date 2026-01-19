package com.hft.algo.base;

import com.hft.core.model.OrderSide;
import com.hft.core.model.OrderType;
import com.hft.core.model.Symbol;
import com.hft.core.model.TimeInForce;

/**
 * Represents an order request from an algorithm.
 */
public class OrderRequest {
    private final Symbol symbol;
    private final OrderSide side;
    private final OrderType type;
    private final long quantity;
    private final long price;
    private final TimeInForce timeInForce;
    private final String algorithmId;
    private final String parentOrderId;

    private OrderRequest(Builder builder) {
        this.symbol = builder.symbol;
        this.side = builder.side;
        this.type = builder.type;
        this.quantity = builder.quantity;
        this.price = builder.price;
        this.timeInForce = builder.timeInForce;
        this.algorithmId = builder.algorithmId;
        this.parentOrderId = builder.parentOrderId;
    }

    public static Builder builder() {
        return new Builder();
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

    public long getQuantity() {
        return quantity;
    }

    public long getPrice() {
        return price;
    }

    public TimeInForce getTimeInForce() {
        return timeInForce;
    }

    public String getAlgorithmId() {
        return algorithmId;
    }

    public String getParentOrderId() {
        return parentOrderId;
    }

    public static class Builder {
        private Symbol symbol;
        private OrderSide side;
        private OrderType type = OrderType.LIMIT;
        private long quantity;
        private long price;
        private TimeInForce timeInForce = TimeInForce.DAY;
        private String algorithmId;
        private String parentOrderId;

        public Builder symbol(Symbol symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder side(OrderSide side) {
            this.side = side;
            return this;
        }

        public Builder type(OrderType type) {
            this.type = type;
            return this;
        }

        public Builder quantity(long quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder price(long price) {
            this.price = price;
            return this;
        }

        public Builder timeInForce(TimeInForce timeInForce) {
            this.timeInForce = timeInForce;
            return this;
        }

        public Builder algorithmId(String algorithmId) {
            this.algorithmId = algorithmId;
            return this;
        }

        public Builder parentOrderId(String parentOrderId) {
            this.parentOrderId = parentOrderId;
            return this;
        }

        public OrderRequest build() {
            if (symbol == null) throw new IllegalStateException("Symbol is required");
            if (side == null) throw new IllegalStateException("Side is required");
            if (quantity <= 0) throw new IllegalStateException("Quantity must be positive");
            return new OrderRequest(this);
        }
    }

    @Override
    public String toString() {
        return String.format("OrderRequest{symbol=%s, side=%s, qty=%d, price=%d, algo=%s}",
                symbol, side, quantity, price, algorithmId);
    }
}
