package com.hft.persistence.chronicle;

import com.hft.core.model.*;
import net.openhft.chronicle.wire.SelfDescribingMarshallable;

/**
 * Chronicle Wire format for Order serialization.
 */
public class OrderWire extends SelfDescribingMarshallable {
    private long clientOrderId;
    private String exchangeOrderId;
    private String ticker;
    private String exchange;
    private String side;
    private String type;
    private String status;
    private String timeInForce;
    private long price;
    private long stopPrice;
    private long quantity;
    private long filledQuantity;
    private long averageFilledPrice;
    private long createdAt;
    private long submittedAt;
    private long acceptedAt;
    private long lastUpdatedAt;
    private String strategyId;
    private String rejectReason;

    public OrderWire() {
    }

    public static OrderWire from(Order order) {
        OrderWire wire = new OrderWire();
        wire.clientOrderId = order.getClientOrderId();
        wire.exchangeOrderId = order.getExchangeOrderId();
        wire.ticker = order.getSymbol().getTicker();
        wire.exchange = order.getSymbol().getExchange().name();
        wire.side = order.getSide() != null ? order.getSide().name() : null;
        wire.type = order.getType() != null ? order.getType().name() : null;
        wire.status = order.getStatus() != null ? order.getStatus().name() : null;
        wire.timeInForce = order.getTimeInForce() != null ? order.getTimeInForce().name() : null;
        wire.price = order.getPrice();
        wire.stopPrice = order.getStopPrice();
        wire.quantity = order.getQuantity();
        wire.filledQuantity = order.getFilledQuantity();
        wire.averageFilledPrice = order.getAverageFilledPrice();
        wire.createdAt = order.getCreatedAt();
        wire.submittedAt = order.getSubmittedAt();
        wire.acceptedAt = order.getAcceptedAt();
        wire.lastUpdatedAt = order.getLastUpdatedAt();
        wire.strategyId = order.getStrategyId();
        wire.rejectReason = order.getRejectReason();
        return wire;
    }

    public Order toOrder() {
        Order order = new Order()
                .symbol(new Symbol(ticker, Exchange.valueOf(exchange)))
                .side(side != null ? OrderSide.valueOf(side) : null)
                .type(type != null ? OrderType.valueOf(type) : null)
                .timeInForce(timeInForce != null ? TimeInForce.valueOf(timeInForce) : null)
                .price(price)
                .quantity(quantity)
                .strategyId(strategyId);

        order.setClientOrderId(clientOrderId);
        order.setStopPrice(stopPrice);

        // Restore state based on status
        if (status != null) {
            OrderStatus orderStatus = OrderStatus.valueOf(status);
            if (orderStatus == OrderStatus.SUBMITTED || orderStatus.ordinal() > OrderStatus.SUBMITTED.ordinal()) {
                order.markSubmitted();
            }
            if (exchangeOrderId != null && (orderStatus == OrderStatus.ACCEPTED || orderStatus.ordinal() > OrderStatus.ACCEPTED.ordinal())) {
                order.markAccepted(exchangeOrderId);
            }
            if (orderStatus == OrderStatus.REJECTED) {
                order.markRejected();
                order.setRejectReason(rejectReason);
            }
            if (filledQuantity > 0) {
                // Partial or full fill - this is approximate since we don't have individual fills
                if (filledQuantity >= quantity) {
                    order.markFilled(filledQuantity, averageFilledPrice);
                } else {
                    order.markPartiallyFilled(filledQuantity, averageFilledPrice);
                }
            }
            if (orderStatus == OrderStatus.CANCELLED) {
                order.markCancelled();
            }
        }

        // Restore persisted timestamps (overrides values set by mark* methods)
        if (createdAt > 0) {
            order.setCreatedAt(createdAt);
        }
        if (lastUpdatedAt > 0) {
            order.setLastUpdatedAt(lastUpdatedAt);
        }

        return order;
    }

    public long getClientOrderId() { return clientOrderId; }
    public String getExchangeOrderId() { return exchangeOrderId; }
    public String getTicker() { return ticker; }
    public String getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
}
