package com.hft.api.config;

import com.hft.engine.service.RiskManager;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for risk limits.
 * Values can be overridden per profile (stub/test/prod) via application-{profile}.properties.
 * All monetary values are in cents for precision.
 */
@Component
@ConfigurationProperties(prefix = "hft.risk")
public class RiskLimitsProperties {

    private long maxOrderSize = 10_000;
    private long maxOrderNotional = 1_000_000;  // $10k in cents
    private long maxPositionSize = 100_000;
    private long maxOrdersPerDay = 10_000;
    private long maxDailyNotional = 10_000_000;  // $100k in cents
    private long maxDailyLoss = 100_000;  // $1k in cents
    private long maxDrawdownPerPosition = 50_000;  // $500 in cents
    private long maxUnrealizedLossPerPosition = 25_000;  // $250 in cents
    private long maxNetExposure = 5_000_000;  // $50k in cents

    /**
     * Converts these properties to a RiskLimits record used by the trading engine.
     */
    public RiskManager.RiskLimits toRiskLimits() {
        return new RiskManager.RiskLimits(
                maxOrderSize,
                maxOrderNotional,
                maxPositionSize,
                maxOrdersPerDay,
                maxDailyNotional,
                maxDailyLoss,
                maxDrawdownPerPosition,
                maxUnrealizedLossPerPosition,
                maxNetExposure
        );
    }

    // Getters and setters

    public long getMaxOrderSize() {
        return maxOrderSize;
    }

    public void setMaxOrderSize(long maxOrderSize) {
        this.maxOrderSize = maxOrderSize;
    }

    public long getMaxOrderNotional() {
        return maxOrderNotional;
    }

    public void setMaxOrderNotional(long maxOrderNotional) {
        this.maxOrderNotional = maxOrderNotional;
    }

    public long getMaxPositionSize() {
        return maxPositionSize;
    }

    public void setMaxPositionSize(long maxPositionSize) {
        this.maxPositionSize = maxPositionSize;
    }

    public long getMaxOrdersPerDay() {
        return maxOrdersPerDay;
    }

    public void setMaxOrdersPerDay(long maxOrdersPerDay) {
        this.maxOrdersPerDay = maxOrdersPerDay;
    }

    public long getMaxDailyNotional() {
        return maxDailyNotional;
    }

    public void setMaxDailyNotional(long maxDailyNotional) {
        this.maxDailyNotional = maxDailyNotional;
    }

    public long getMaxDailyLoss() {
        return maxDailyLoss;
    }

    public void setMaxDailyLoss(long maxDailyLoss) {
        this.maxDailyLoss = maxDailyLoss;
    }

    public long getMaxDrawdownPerPosition() {
        return maxDrawdownPerPosition;
    }

    public void setMaxDrawdownPerPosition(long maxDrawdownPerPosition) {
        this.maxDrawdownPerPosition = maxDrawdownPerPosition;
    }

    public long getMaxUnrealizedLossPerPosition() {
        return maxUnrealizedLossPerPosition;
    }

    public void setMaxUnrealizedLossPerPosition(long maxUnrealizedLossPerPosition) {
        this.maxUnrealizedLossPerPosition = maxUnrealizedLossPerPosition;
    }

    public long getMaxNetExposure() {
        return maxNetExposure;
    }

    public void setMaxNetExposure(long maxNetExposure) {
        this.maxNetExposure = maxNetExposure;
    }
}
