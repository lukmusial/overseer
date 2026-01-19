package com.hft.api.dto;

import com.hft.core.model.OrderSide;
import com.hft.core.model.OrderType;
import com.hft.core.model.TimeInForce;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotBlank String symbol,
        @NotBlank String exchange,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        TimeInForce timeInForce,
        @Min(1) long quantity,
        long price,
        long stopPrice,
        String strategyId
) {}
