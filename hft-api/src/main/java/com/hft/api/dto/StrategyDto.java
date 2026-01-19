package com.hft.api.dto;

import com.hft.algo.base.AlgorithmState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record StrategyDto(
        String id,
        @NotBlank String name,
        @NotBlank String type,
        AlgorithmState state,
        @NotNull List<String> symbols,
        Map<String, Object> parameters,
        double progress,
        StrategyStatsDto stats
) {
    public record StrategyStatsDto(
            long startTimeNanos,
            long endTimeNanos,
            long totalOrders,
            long filledOrders,
            long cancelledOrders,
            long rejectedOrders,
            long realizedPnl,
            long unrealizedPnl,
            long maxDrawdown
    ) {}
}
