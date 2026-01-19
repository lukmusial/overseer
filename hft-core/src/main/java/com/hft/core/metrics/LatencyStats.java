package com.hft.core.metrics;

/**
 * Immutable latency statistics.
 * All values are in nanoseconds.
 */
public record LatencyStats(
        long count,
        long min,
        long max,
        long mean,
        long p50,
        long p90,
        long p95,
        long p99,
        long p999
) {
    public static LatencyStats empty() {
        return new LatencyStats(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Returns min latency in microseconds.
     */
    public double minMicros() {
        return min / 1000.0;
    }

    /**
     * Returns max latency in microseconds.
     */
    public double maxMicros() {
        return max / 1000.0;
    }

    /**
     * Returns mean latency in microseconds.
     */
    public double meanMicros() {
        return mean / 1000.0;
    }

    /**
     * Returns p50 latency in microseconds.
     */
    public double p50Micros() {
        return p50 / 1000.0;
    }

    /**
     * Returns p90 latency in microseconds.
     */
    public double p90Micros() {
        return p90 / 1000.0;
    }

    /**
     * Returns p95 latency in microseconds.
     */
    public double p95Micros() {
        return p95 / 1000.0;
    }

    /**
     * Returns p99 latency in microseconds.
     */
    public double p99Micros() {
        return p99 / 1000.0;
    }

    /**
     * Returns p99.9 latency in microseconds.
     */
    public double p999Micros() {
        return p999 / 1000.0;
    }

    /**
     * Returns min latency in milliseconds.
     */
    public double minMillis() {
        return min / 1_000_000.0;
    }

    /**
     * Returns max latency in milliseconds.
     */
    public double maxMillis() {
        return max / 1_000_000.0;
    }

    /**
     * Returns mean latency in milliseconds.
     */
    public double meanMillis() {
        return mean / 1_000_000.0;
    }

    /**
     * Returns p99 latency in milliseconds.
     */
    public double p99Millis() {
        return p99 / 1_000_000.0;
    }

    @Override
    public String toString() {
        return String.format(
                "LatencyStats{count=%d, min=%.2fus, max=%.2fus, mean=%.2fus, p50=%.2fus, p90=%.2fus, p95=%.2fus, p99=%.2fus, p99.9=%.2fus}",
                count, minMicros(), maxMicros(), meanMicros(), p50Micros(), p90Micros(), p95Micros(), p99Micros(), p999Micros()
        );
    }
}
