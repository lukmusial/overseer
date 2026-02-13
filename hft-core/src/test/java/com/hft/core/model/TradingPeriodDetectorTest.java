package com.hft.core.model;

import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class TradingPeriodDetectorTest {

    @Test
    void currentPeriod_duringOverlap_shouldReturnOverlap() {
        Clock fixedClock = Clock.fixed(
                LocalDate.of(2026, 2, 12).atTime(14, 0).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC
        );
        TradingPeriodDetector detector = new TradingPeriodDetector(fixedClock);

        assertEquals(TradingPeriod.OVERLAP, detector.currentPeriod());
    }

    @Test
    void currentPeriod_duringLondonOpen_shouldReturnLondonOpen() {
        Clock fixedClock = Clock.fixed(
                LocalDate.of(2026, 2, 12).atTime(8, 30).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC
        );
        TradingPeriodDetector detector = new TradingPeriodDetector(fixedClock);

        assertEquals(TradingPeriod.LONDON_OPEN, detector.currentPeriod());
    }

    @Test
    void currentPeriod_duringOffHours_shouldReturnOffHours() {
        Clock fixedClock = Clock.fixed(
                LocalDate.of(2026, 2, 12).atTime(22, 0).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC
        );
        TradingPeriodDetector detector = new TradingPeriodDetector(fixedClock);

        assertEquals(TradingPeriod.OFF_HOURS, detector.currentPeriod());
    }

    @Test
    void currentMultiplier_duringOverlap_shouldReturn1() {
        Clock fixedClock = Clock.fixed(
                LocalDate.of(2026, 2, 12).atTime(13, 0).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC
        );
        TradingPeriodDetector detector = new TradingPeriodDetector(fixedClock);

        assertEquals(1.00, detector.currentMultiplier());
    }

    @Test
    void currentMultiplier_duringOffHours_shouldReturn025() {
        Clock fixedClock = Clock.fixed(
                LocalDate.of(2026, 2, 12).atTime(3, 0).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC
        );
        TradingPeriodDetector detector = new TradingPeriodDetector(fixedClock);

        assertEquals(0.25, detector.currentMultiplier());
    }

    @Test
    void defaultConstructor_shouldNotThrow() {
        TradingPeriodDetector detector = new TradingPeriodDetector();
        assertNotNull(detector.currentPeriod());
    }
}
