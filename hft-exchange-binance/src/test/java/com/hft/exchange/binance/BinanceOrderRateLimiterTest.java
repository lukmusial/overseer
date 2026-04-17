package com.hft.exchange.binance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinanceOrderRateLimiterTest {

    @Test
    void shouldAllowOrdersUpToLimit() {
        var limiter = new BinanceOrderRateLimiter(5);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(), "Order " + i + " should be allowed");
        }
        assertFalse(limiter.tryAcquire(), "Order 6 should be rejected (limit=5)");
    }

    @Test
    void shouldTrackCurrentCount() {
        var limiter = new BinanceOrderRateLimiter(10);

        assertEquals(0, limiter.getCurrentCount());

        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();

        assertEquals(3, limiter.getCurrentCount());
    }

    @Test
    void shouldReturnMaxOrders() {
        var limiter = new BinanceOrderRateLimiter(50);
        assertEquals(50, limiter.getMaxOrders());
    }

    @Test
    void defaultLimitIs50() {
        var limiter = new BinanceOrderRateLimiter();
        assertEquals(50, limiter.getMaxOrders());

        // Should allow 50
        for (int i = 0; i < 50; i++) {
            assertTrue(limiter.tryAcquire());
        }
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void shouldNotCountRejectedAcquires() {
        var limiter = new BinanceOrderRateLimiter(3);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());

        // These are rejected — should not increase count
        assertFalse(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        assertEquals(3, limiter.getCurrentCount());
    }
}
