package com.hft.core.metrics;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free latency histogram for tracking latency distributions.
 * Uses a log-linear bucketing scheme for efficient memory usage.
 */
public class LatencyHistogram {
    // Bucket configuration
    // Buckets: 0-1us, 1-2us, 2-4us, 4-8us, ... up to ~1 second
    private static final int NUM_BUCKETS = 64;
    private static final long MIN_VALUE = 1_000L; // 1 microsecond in nanos

    private final LongAdder[] buckets;
    private final LongAdder count;
    private final LongAdder sum;
    private final AtomicLong min;
    private final AtomicLong max;

    public LatencyHistogram() {
        this.buckets = new LongAdder[NUM_BUCKETS];
        for (int i = 0; i < NUM_BUCKETS; i++) {
            buckets[i] = new LongAdder();
        }
        this.count = new LongAdder();
        this.sum = new LongAdder();
        this.min = new AtomicLong(Long.MAX_VALUE);
        this.max = new AtomicLong(Long.MIN_VALUE);
    }

    /**
     * Records a latency value in nanoseconds.
     */
    public void record(long valueNanos) {
        if (valueNanos < 0) {
            return;
        }

        int bucket = getBucket(valueNanos);
        buckets[bucket].increment();
        count.increment();
        sum.add(valueNanos);

        // Update min/max atomically
        updateMin(valueNanos);
        updateMax(valueNanos);
    }

    private int getBucket(long value) {
        if (value < MIN_VALUE) {
            return 0;
        }
        // Log2-based bucketing
        int bucket = 64 - Long.numberOfLeadingZeros(value / MIN_VALUE);
        return Math.min(bucket, NUM_BUCKETS - 1);
    }

    private long getBucketUpperBound(int bucket) {
        if (bucket == 0) {
            return MIN_VALUE;
        }
        return MIN_VALUE << bucket;
    }

    private void updateMin(long value) {
        long current;
        do {
            current = min.get();
            if (value >= current) {
                return;
            }
        } while (!min.compareAndSet(current, value));
    }

    private void updateMax(long value) {
        long current;
        do {
            current = max.get();
            if (value <= current) {
                return;
            }
        } while (!max.compareAndSet(current, value));
    }

    /**
     * Returns the approximate percentile value.
     */
    public long getPercentile(double percentile) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("Percentile must be between 0 and 100");
        }

        long totalCount = count.sum();
        if (totalCount == 0) {
            return 0;
        }

        long targetCount = (long) (totalCount * percentile / 100.0);
        long cumulative = 0;

        for (int i = 0; i < NUM_BUCKETS; i++) {
            cumulative += buckets[i].sum();
            if (cumulative >= targetCount) {
                return getBucketUpperBound(i);
            }
        }

        return max.get();
    }

    /**
     * Returns statistics for this histogram.
     */
    public LatencyStats getStats() {
        long totalCount = count.sum();
        if (totalCount == 0) {
            return LatencyStats.empty();
        }

        long totalSum = sum.sum();
        long minVal = min.get();
        long maxVal = max.get();

        if (minVal == Long.MAX_VALUE) minVal = 0;
        if (maxVal == Long.MIN_VALUE) maxVal = 0;

        return new LatencyStats(
                totalCount,
                minVal,
                maxVal,
                totalSum / totalCount,
                getPercentile(50),
                getPercentile(90),
                getPercentile(95),
                getPercentile(99),
                getPercentile(99.9)
        );
    }

    public void reset() {
        for (LongAdder bucket : buckets) {
            bucket.reset();
        }
        count.reset();
        sum.reset();
        min.set(Long.MAX_VALUE);
        max.set(Long.MIN_VALUE);
    }
}
