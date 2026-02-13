package com.hft.core.model;

import java.time.LocalTime;
import java.util.List;

/**
 * Trading periods based on UK business hours (UTC).
 *
 * Each period defines a time window, a position size multiplier,
 * and a list of recommended strategy types for that window.
 */
public enum TradingPeriod {

    LONDON_OPEN(
            LocalTime.of(8, 0), LocalTime.of(9, 0),
            0.75,
            List.of("bollinger_squeeze", "ema_adx_rsi")
    ),
    EU_MORNING(
            LocalTime.of(9, 0), LocalTime.of(11, 0),
            0.75,
            List.of("ema_adx_rsi", "vwap_mean_reversion")
    ),
    PRE_OVERLAP(
            LocalTime.of(11, 0), LocalTime.of(12, 0),
            0.50,
            List.of("bollinger_squeeze", "vwap_mean_reversion")
    ),
    OVERLAP(
            LocalTime.of(12, 0), LocalTime.of(16, 0),
            1.00,
            List.of("ema_adx_rsi", "bollinger_squeeze")
    ),
    POST_EU(
            LocalTime.of(16, 0), LocalTime.of(18, 0),
            0.50,
            List.of("vwap_mean_reversion")
    ),
    OFF_HOURS(
            LocalTime.of(18, 0), LocalTime.of(8, 0),
            0.25,
            List.of()
    );

    private final LocalTime startTime;
    private final LocalTime endTime;
    private final double positionMultiplier;
    private final List<String> recommendedStrategies;

    TradingPeriod(LocalTime startTime, LocalTime endTime, double positionMultiplier,
                  List<String> recommendedStrategies) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.positionMultiplier = positionMultiplier;
        this.recommendedStrategies = recommendedStrategies;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public double getPositionMultiplier() {
        return positionMultiplier;
    }

    public List<String> getRecommendedStrategies() {
        return recommendedStrategies;
    }

    /**
     * Determines the trading period for a given UTC time.
     */
    public static TradingPeriod fromUtcTime(LocalTime utcTime) {
        for (TradingPeriod period : values()) {
            if (period == OFF_HOURS) {
                continue; // Handle wrap-around last
            }
            if (!utcTime.isBefore(period.startTime) && utcTime.isBefore(period.endTime)) {
                return period;
            }
        }
        return OFF_HOURS;
    }
}
