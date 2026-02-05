package com.hft.api.dto;

/**
 * Request to update risk limits.
 */
public record UpdateRiskLimitsRequest(
        long maxOrderSize,
        long maxOrderNotional,
        long maxPositionSize,
        long maxOrdersPerDay,
        long maxDailyNotional,
        long maxDailyLoss,
        long maxDrawdownPerPosition,
        long maxUnrealizedLossPerPosition,
        long maxNetExposure
) {}
