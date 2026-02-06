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
        int priceScale,
        OrderStatus status,
        String rejectReason,
        String strategyId,
        String strategyName,
        long createdAt,
        long updatedAt
) {
    /**
     * Converts epoch nanos to epoch millis for frontend display.
     * Detects stale nanoTime values (from before the epoch-nanos fix) and returns 0.
     */
    private static long toEpochMillis(long epochNanos) {
        if (epochNanos <= 0) return 0;
        // Epoch nanos for year 2000 ≈ 9.47e17; System.nanoTime() values are ≈ 1e12-1e13
        if (epochNanos < 946_684_800_000_000_000L) return 0;
        return epochNanos / 1_000_000;
    }

    public static OrderDto from(com.hft.core.model.Order order, String strategyName) {
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
                order.getPriceScale(),
                order.getStatus(),
                order.getRejectReason(),
                order.getStrategyId(),
                strategyName,
                toEpochMillis(order.getCreatedAt()),
                toEpochMillis(order.getLastUpdatedAt())
        );
    }

    public static OrderDto from(com.hft.core.model.Order order) {
        return from(order, null);
    }
}
