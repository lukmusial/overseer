package com.hft.api.dto;

import com.hft.engine.service.RiskManager;

/**
 * DTO for exposing risk limits configuration and current usage to the frontend.
 */
public record RiskLimitsDto(
        LimitsConfigDto limits,
        UsageDto usage,
        boolean tradingEnabled,
        String disabledReason
) {
    /**
     * All configured risk limits.
     */
    public record LimitsConfigDto(
            long maxOrderSize,
            long maxOrderNotional,
            long maxPositionSize,
            long maxOrdersPerDay,
            long maxDailyNotional,
            long maxDailyLoss,
            long maxDrawdownPerPosition,
            long maxUnrealizedLossPerPosition,
            long maxNetExposure
    ) {
        public static LimitsConfigDto from(RiskManager.RiskLimits limits) {
            return new LimitsConfigDto(
                    limits.maxOrderSize(),
                    limits.maxOrderNotional(),
                    limits.maxPositionSize(),
                    limits.maxOrdersPerDay(),
                    limits.maxDailyNotional(),
                    limits.maxDailyLoss(),
                    limits.maxDrawdownPerPosition(),
                    limits.maxUnrealizedLossPerPosition(),
                    limits.maxNetExposure()
            );
        }
    }

    /**
     * Current usage against limits.
     */
    public record UsageDto(
            long ordersSubmittedToday,
            long notionalTradedToday,
            long currentDailyPnl,
            long currentNetExposure
    ) {}

    /**
     * Creates a RiskLimitsDto from the RiskManager and position data.
     */
    public static RiskLimitsDto from(RiskManager riskManager, long totalPnlCents, long netExposure) {
        RiskManager.RiskLimits limits = riskManager.getLimits();
        return new RiskLimitsDto(
                LimitsConfigDto.from(limits),
                new UsageDto(
                        riskManager.getOrdersSubmittedToday(),
                        riskManager.getNotionalTradedToday(),
                        totalPnlCents,
                        netExposure
                ),
                riskManager.isTradingEnabled(),
                riskManager.getDisabledReason()
        );
    }
}
