package com.hft.core.model;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * Detects the current trading period based on a configurable clock.
 * Uses UTC time zone for all period calculations.
 */
public class TradingPeriodDetector {

    private final Clock clock;

    public TradingPeriodDetector() {
        this(Clock.systemUTC());
    }

    public TradingPeriodDetector(Clock clock) {
        this.clock = clock;
    }

    /**
     * Returns the current trading period based on UTC time.
     */
    public TradingPeriod currentPeriod() {
        LocalTime utcTime = LocalTime.now(clock);
        return TradingPeriod.fromUtcTime(utcTime);
    }

    /**
     * Returns the position size multiplier for the current period.
     */
    public double currentMultiplier() {
        return currentPeriod().getPositionMultiplier();
    }
}
