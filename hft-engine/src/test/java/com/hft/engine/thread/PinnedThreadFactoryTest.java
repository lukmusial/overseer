package com.hft.engine.thread;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PinnedThreadFactoryTest {

    @Test
    void disabledFactoryProducesNamedDaemonThread() throws InterruptedException {
        var factory = new PinnedThreadFactory("test-disabled");
        assertFalse(factory.isPinningRequested());
        assertFalse(factory.isPinningActive());

        var latch = new CountDownLatch(1);
        var capturedName = new AtomicReference<String>();
        var capturedDaemon = new AtomicReference<Boolean>();

        Thread t = factory.newThread(() -> {
            capturedName.set(Thread.currentThread().getName());
            capturedDaemon.set(Thread.currentThread().isDaemon());
            latch.countDown();
        });
        t.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "thread should start");

        assertTrue(capturedName.get().startsWith("test-disabled-"),
                "expected name to start with 'test-disabled-', got: " + capturedName.get());
        assertTrue(capturedDaemon.get(), "thread should be daemon");
    }

    @Test
    void enabledFactoryRequestsPinningAndProducesDaemonThread() throws InterruptedException {
        // On macOS/Windows the underlying AffinityThreadFactory cannot actually pin,
        // but it must still produce runnable daemon threads and the factory must
        // report that pinning was requested.
        var factory = new PinnedThreadFactory("test-pinned", true, AffinityStrategyType.ANY);
        assertTrue(factory.isPinningRequested());

        var latch = new CountDownLatch(1);
        var capturedDaemon = new AtomicReference<Boolean>();

        Thread t = factory.newThread(() -> {
            capturedDaemon.set(Thread.currentThread().isDaemon());
            latch.countDown();
        });
        t.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "thread should start");
        assertTrue(capturedDaemon.get(), "thread should be daemon");
    }

    @Test
    void getNamePrefixReturnsConfigured() {
        var factory = new PinnedThreadFactory("my-prefix");
        assertEquals("my-prefix", factory.getNamePrefix());
    }

    @Test
    void distinctThreadsGetDistinctNames() {
        var factory = new PinnedThreadFactory("seq");
        Thread a = factory.newThread(() -> {});
        Thread b = factory.newThread(() -> {});
        assertNotEquals(a.getName(), b.getName(), "sequential threads must have distinct names");
    }
}
