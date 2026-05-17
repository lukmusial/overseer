package com.hft.core.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PaddedVolatileLongTest {

    @Test
    void defaultInitialValueIsZero() {
        var p = new PaddedVolatileLong();
        assertEquals(0L, p.get());
    }

    @Test
    void constructorAcceptsInitialValue() {
        var p = new PaddedVolatileLong(42L);
        assertEquals(42L, p.get());
    }

    @Test
    void setReplacesValue() {
        var p = new PaddedVolatileLong(1L);
        p.set(99L);
        assertEquals(99L, p.get());
    }

    @Test
    void addAndGetReturnsPostIncrementValue() {
        var p = new PaddedVolatileLong(100L);
        assertEquals(150L, p.addAndGet(50));
        assertEquals(150L, p.get());
        assertEquals(140L, p.addAndGet(-10));
    }

    @Test
    void volatileSemanticsObservableAcrossThreads() throws InterruptedException {
        var p = new PaddedVolatileLong();
        var started = new CountDownLatch(1);
        var seen = new AtomicLong(-1);

        Thread reader = new Thread(() -> {
            started.countDown();
            while (true) {
                long v = p.get();
                if (v >= 1_000) {
                    seen.set(v);
                    return;
                }
                Thread.onSpinWait();
            }
        });
        reader.setDaemon(true);
        reader.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));
        for (long i = 1; i <= 1_000; i++) {
            p.set(i);
        }

        reader.join(2_000);
        assertEquals(1_000L, seen.get(),
                "reader thread must observe writer's final value via volatile semantics");
    }

    @Test
    void toStringReturnsDecimalRepresentation() {
        assertEquals("7", new PaddedVolatileLong(7).toString());
    }
}
