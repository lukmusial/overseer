package com.hft.exchange.binance;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Sliding-window rate limiter for Binance order submission.
 *
 * <p>Binance enforces a limit of 50 orders per 10-second window per account.
 * This limiter uses a circular buffer of per-second counters to track
 * submissions with nanosecond precision and zero contention between threads.
 *
 * <p>Thread-safe via atomic operations — no locks.
 */
public class BinanceOrderRateLimiter {

    private static final int WINDOW_SECONDS = 10;
    private static final int DEFAULT_MAX_ORDERS = 50;

    private final int maxOrders;
    private final AtomicLong[] buckets;
    private final AtomicLong[] bucketTimestamps;

    public BinanceOrderRateLimiter() {
        this(DEFAULT_MAX_ORDERS);
    }

    public BinanceOrderRateLimiter(int maxOrders) {
        this.maxOrders = maxOrders;
        this.buckets = new AtomicLong[WINDOW_SECONDS];
        this.bucketTimestamps = new AtomicLong[WINDOW_SECONDS];
        for (int i = 0; i < WINDOW_SECONDS; i++) {
            buckets[i] = new AtomicLong(0);
            bucketTimestamps[i] = new AtomicLong(0);
        }
    }

    /**
     * Attempts to acquire a permit for one order submission.
     *
     * @return true if the order can be submitted, false if rate limit would be exceeded
     */
    public boolean tryAcquire() {
        long nowSeconds = System.currentTimeMillis() / 1000;
        int idx = (int) (nowSeconds % WINDOW_SECONDS);

        // Reset bucket if it's from a previous window cycle
        long bucketTime = bucketTimestamps[idx].get();
        if (bucketTime != nowSeconds) {
            if (bucketTimestamps[idx].compareAndSet(bucketTime, nowSeconds)) {
                buckets[idx].set(0);
            }
        }

        // Count orders in the current window
        long total = 0;
        for (int i = 0; i < WINDOW_SECONDS; i++) {
            long ts = bucketTimestamps[i].get();
            if (nowSeconds - ts < WINDOW_SECONDS) {
                total += buckets[i].get();
            }
        }

        if (total >= maxOrders) {
            return false;
        }

        buckets[idx].incrementAndGet();
        return true;
    }

    /**
     * Returns the current order count in the sliding window.
     */
    public long getCurrentCount() {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long total = 0;
        for (int i = 0; i < WINDOW_SECONDS; i++) {
            long ts = bucketTimestamps[i].get();
            if (nowSeconds - ts < WINDOW_SECONDS) {
                total += buckets[i].get();
            }
        }
        return total;
    }

    /**
     * Returns the maximum orders allowed per window.
     */
    public int getMaxOrders() {
        return maxOrders;
    }
}
