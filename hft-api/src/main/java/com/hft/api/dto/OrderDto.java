package com.hft.api.dto;

import com.hft.core.model.OrderSide;
import com.hft.core.model.OrderStatus;
import com.hft.core.model.OrderType;
import com.hft.core.model.TimeInForce;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderDto(
        Long clientOrderId,
        String exchangeOrderId,
        @NotBlank String symbol,
        @NotBlank String exchange,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        TimeInForce timeInForce,
        @Min(1) long quantity,
        long price,
        long stopPrice,
        long filledQuantity,
        long averageFilledPrice,
        OrderStatus status,
        String rejectReason,
        String strategyId,
        long createdAt,
        long updatedAt
) {
    public static OrderDto from(com.hft.core.model.Order order) {
        return new OrderDto(
                order.getClientOrderId(),
                order.getExchangeOrderId(),
                order.getSymbol() != null ? order.getSymbol().getTicker() : null,
                order.getSymbol() != null ? order.getSymbol().getExchange().name() : null,
                order.getSide(),
                order.getType(),
                order.getTimeInForce(),
                order.getQuantity(),
                order.getPrice(),
                order.getStopPrice(),
                order.getFilledQuantity(),
                order.getAverageFilledPrice(),
                order.getStatus(),
                order.getRejectReason(),
                order.getStrategyId(),
                order.getCreatedAt(),
                order.getLastUpdatedAt()
        );
    }
}
