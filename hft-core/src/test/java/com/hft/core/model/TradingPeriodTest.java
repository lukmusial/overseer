package com.hft.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class TradingPeriodTest {

    @ParameterizedTest
    @CsvSource({
            "08:00, LONDON_OPEN",
            "08:30, LONDON_OPEN",
            "08:59, LONDON_OPEN",
            "09:00, EU_MORNING",
            "10:00, EU_MORNING",
            "10:59, EU_MORNING",
            "11:00, PRE_OVERLAP",
            "11:30, PRE_OVERLAP",
            "11:59, PRE_OVERLAP",
            "12:00, OVERLAP",
            "14:00, OVERLAP",
            "15:59, OVERLAP",
            "16:00, POST_EU",
            "17:00, POST_EU",
            "17:59, POST_EU",
            "18:00, OFF_HOURS",
            "20:00, OFF_HOURS",
            "23:59, OFF_HOURS",
            "00:00, OFF_HOURS",
            "03:00, OFF_HOURS",
            "07:59, OFF_HOURS"
    })
    void fromUtcTime_shouldReturnCorrectPeriod(String timeStr, String expectedPeriod) {
        LocalTime time = LocalTime.parse(timeStr);
        assertEquals(TradingPeriod.valueOf(expectedPeriod), TradingPeriod.fromUtcTime(time));
    }

    @Test
    void positionMultipliers_shouldMatchSpecification() {
        assertEquals(0.75, TradingPeriod.LONDON_OPEN.getPositionMultiplier());
        assertEquals(0.75, TradingPeriod.EU_MORNING.getPositionMultiplier());
        assertEquals(0.50, TradingPeriod.PRE_OVERLAP.getPositionMultiplier());
        assertEquals(1.00, TradingPeriod.OVERLAP.getPositionMultiplier());
        assertEquals(0.50, TradingPeriod.POST_EU.getPositionMultiplier());
        assertEquals(0.25, TradingPeriod.OFF_HOURS.getPositionMultiplier());
    }

    @Test
    void recommendedStrategies_londonOpen() {
        var strategies = TradingPeriod.LONDON_OPEN.getRecommendedStrategies();
        assertEquals(2, strategies.size());
        assertTrue(strategies.contains("bollinger_squeeze"));
        assertTrue(strategies.contains("ema_adx_rsi"));
    }

    @Test
    void recommendedStrategies_euMorning() {
        var strategies = TradingPeriod.EU_MORNING.getRecommendedStrategies();
        assertEquals(2, strategies.size());
        assertTrue(strategies.contains("ema_adx_rsi"));
        assertTrue(strategies.contains("vwap_mean_reversion"));
    }

    @Test
    void recommendedStrategies_overlap() {
        var strategies = TradingPeriod.OVERLAP.getRecommendedStrategies();
        assertEquals(2, strategies.size());
        assertTrue(strategies.contains("ema_adx_rsi"));
        assertTrue(strategies.contains("bollinger_squeeze"));
    }

    @Test
    void recommendedStrategies_postEu() {
        var strategies = TradingPeriod.POST_EU.getRecommendedStrategies();
        assertEquals(1, strategies.size());
        assertTrue(strategies.contains("vwap_mean_reversion"));
    }

    @Test
    void recommendedStrategies_offHours_shouldBeEmpty() {
        assertTrue(TradingPeriod.OFF_HOURS.getRecommendedStrategies().isEmpty());
    }

    @Test
    void allPeriodsHaveStartAndEndTimes() {
        for (TradingPeriod period : TradingPeriod.values()) {
            assertNotNull(period.getStartTime());
            assertNotNull(period.getEndTime());
        }
    }

    @Test
    void boundaryAtMidnight_shouldBeOffHours() {
        assertEquals(TradingPeriod.OFF_HOURS, TradingPeriod.fromUtcTime(LocalTime.MIDNIGHT));
    }

    @Test
    void boundaryBeforeLondonOpen_shouldBeOffHours() {
        assertEquals(TradingPeriod.OFF_HOURS, TradingPeriod.fromUtcTime(LocalTime.of(7, 59)));
    }

    @Test
    void boundaryExactlyAtLondonOpen_shouldBeLondonOpen() {
        assertEquals(TradingPeriod.LONDON_OPEN, TradingPeriod.fromUtcTime(LocalTime.of(8, 0)));
    }
}
