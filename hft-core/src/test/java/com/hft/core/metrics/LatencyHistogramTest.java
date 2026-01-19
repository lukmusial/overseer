package com.hft.core.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LatencyHistogramTest {
    private LatencyHistogram histogram;

    @BeforeEach
    void setUp() {
        histogram = new LatencyHistogram();
    }

    @Test
    void shouldStartEmpty() {
        LatencyStats stats = histogram.getStats();

        assertEquals(0, stats.count());
        assertEquals(0, stats.min());
        assertEquals(0, stats.max());
        assertEquals(0, stats.mean());
    }

    @Test
    void shouldRecordSingleValue() {
        histogram.record(1000); // 1 microsecond

        LatencyStats stats = histogram.getStats();
        assertEquals(1, stats.count());
        assertEquals(1000, stats.min());
        assertEquals(1000, stats.max());
    }

    @Test
    void shouldTrackMinAndMax() {
        histogram.record(5000);
        histogram.record(1000);
        histogram.record(10000);
        histogram.record(3000);

        LatencyStats stats = histogram.getStats();
        assertEquals(1000, stats.min());
        assertEquals(10000, stats.max());
    }

    @Test
    void shouldCalculateMean() {
        histogram.record(1000);
        histogram.record(2000);
        histogram.record(3000);

        LatencyStats stats = histogram.getStats();
        assertEquals(2000, stats.mean());
    }

    @Test
    void shouldCalculatePercentiles() {
        // Add 100 values from 1 to 100 microseconds
        for (int i = 1; i <= 100; i++) {
            histogram.record(i * 1000L);
        }

        LatencyStats stats = histogram.getStats();
        assertEquals(100, stats.count());

        // p50 should be around 50 microseconds (with bucket approximation tolerance)
        assertTrue(stats.p50() >= 30_000 && stats.p50() <= 80_000,
                "p50 should be around 50us, was: " + stats.p50());

        // p99 should be around 99 microseconds (with bucket approximation tolerance)
        assertTrue(stats.p99() >= 64_000 && stats.p99() <= 130_000,
                "p99 should be around 99us, was: " + stats.p99());
    }

    @Test
    void shouldIgnoreNegativeValues() {
        histogram.record(-1000);
        histogram.record(1000);

        LatencyStats stats = histogram.getStats();
        assertEquals(1, stats.count());
    }

    @Test
    void shouldReset() {
        histogram.record(1000);
        histogram.record(2000);
        histogram.record(3000);

        histogram.reset();
        LatencyStats stats = histogram.getStats();

        assertEquals(0, stats.count());
    }

    @Test
    void shouldConvertToMicroseconds() {
        histogram.record(1500); // 1.5 microseconds

        LatencyStats stats = histogram.getStats();
        assertEquals(1.5, stats.minMicros(), 0.01);
    }

    @Test
    void shouldConvertToMilliseconds() {
        histogram.record(1_500_000); // 1.5 milliseconds

        LatencyStats stats = histogram.getStats();
        assertEquals(1.5, stats.minMillis(), 0.01);
    }

    @Test
    void shouldHandleLargeValues() {
        histogram.record(1_000_000_000L); // 1 second

        LatencyStats stats = histogram.getStats();
        assertEquals(1, stats.count());
        assertEquals(1_000_000_000L, stats.min());
    }

    @Test
    void shouldHandleVerySmallValues() {
        histogram.record(100); // 100 nanoseconds

        LatencyStats stats = histogram.getStats();
        assertEquals(1, stats.count());
    }
}
