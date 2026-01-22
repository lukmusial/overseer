package com.hft.api.dto;

import com.hft.engine.TradingEngine;
import com.hft.engine.service.PositionManager;
import com.hft.core.metrics.OrderMetrics;

public record EngineStatusDto(
        boolean running,
        Long startTime,
        long uptimeMillis,
        boolean tradingEnabled,
        String tradingDisabledReason,
        long eventsPublished,
        long ringBufferCapacity,
        int totalOrders,
        int activeOrders,
        int totalOrdersProcessed,
        int totalTradesExecuted,
        int activeStrategies,
        int openPositions,
        int pendingOrders,
        PositionSummaryDto positions,
        MetricsSummaryDto metrics
) {
    public static EngineStatusDto from(TradingEngine.EngineSnapshot snapshot, int activeStrategies) {
        long startTime = snapshot.startTimeMillis();
        return new EngineStatusDto(
                snapshot.running(),
                startTime > 0 ? startTime : null,
                snapshot.uptimeMillis(),
                snapshot.tradingEnabled(),
                snapshot.tradingDisabledReason(),
                snapshot.eventsPublished(),
                snapshot.ringBufferCapacity(),
                snapshot.totalOrders(),
                snapshot.activeOrders(),
                (int) snapshot.metrics().ordersSubmitted(),
                (int) snapshot.metrics().ordersFilled(),
                activeStrategies,
                snapshot.positions().activePositions(),
                snapshot.activeOrders(),
                PositionSummaryDto.from(snapshot.positions()),
                MetricsSummaryDto.from(snapshot.metrics())
        );
    }

    public record PositionSummaryDto(
            int totalPositions,
            int activePositions,
            long realizedPnl,
            long unrealizedPnl,
            long totalPnl,
            long netExposure
    ) {
        public static PositionSummaryDto from(PositionManager.PositionSnapshot snapshot) {
            return new PositionSummaryDto(
                    snapshot.totalPositions(),
                    snapshot.activePositions(),
                    snapshot.realizedPnl(),
                    snapshot.unrealizedPnl(),
                    snapshot.totalPnl(),
                    snapshot.netExposure()
            );
        }
    }

    public record MetricsSummaryDto(
            long ordersSubmitted,
            long ordersAccepted,
            long ordersFilled,
            long ordersCancelled,
            long ordersRejected,
            double fillRate,
            double rejectRate,
            double throughputPerSecond,
            long totalQuantityFilled,
            long totalNotionalValue
    ) {
        public static MetricsSummaryDto from(OrderMetrics.MetricsSnapshot snapshot) {
            return new MetricsSummaryDto(
                    snapshot.ordersSubmitted(),
                    snapshot.ordersAccepted(),
                    snapshot.ordersFilled(),
                    snapshot.ordersCancelled(),
                    snapshot.ordersRejected(),
                    snapshot.fillRate(),
                    snapshot.rejectRate(),
                    snapshot.throughputPerSecond(),
                    snapshot.totalQuantityFilled(),
                    snapshot.totalNotionalValue()
            );
        }
    }
}
