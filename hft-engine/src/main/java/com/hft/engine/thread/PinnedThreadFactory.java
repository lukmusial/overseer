package com.hft.engine.thread;

import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityStrategy;
import net.openhft.affinity.AffinityThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadFactory that produces named daemon threads, optionally pinned to a CPU core
 * via OpenHFT's AffinityThreadFactory.
 *
 * On Linux, enabling pinning causes the produced thread to acquire an AffinityLock
 * for the lifetime of its run — the kernel will schedule it onto one dedicated logical CPU.
 * On macOS and Windows, the underlying OpenHFT library reports no suitable native hook
 * and the thread runs unpinned; we still emit named threads so thread dumps are useful.
 *
 * If affinity acquisition or the OpenHFT static initializer throws on an exotic platform,
 * the factory logs a single WARN and falls back to a plain named-daemon factory — the
 * engine never fails to start because pinning wasn't achievable.
 */
public final class PinnedThreadFactory implements ThreadFactory {

    private static final Logger log = LoggerFactory.getLogger(PinnedThreadFactory.class);

    private final String namePrefix;
    private final boolean pinRequested;
    private final ThreadFactory delegate;
    private final AtomicInteger counter = new AtomicInteger(0);

    public PinnedThreadFactory(String namePrefix) {
        this(namePrefix, false, AffinityStrategyType.ANY);
    }

    /**
     * Primary constructor. Takes the enum wrapper so callers (hft-api config, tests, etc.)
     * do not need to import OpenHFT types directly.
     */
    public PinnedThreadFactory(String namePrefix, boolean pinEnabled, AffinityStrategyType... strategies) {
        this.namePrefix = namePrefix;
        this.pinRequested = pinEnabled;
        this.delegate = pinEnabled ? buildPinningDelegate(namePrefix, strategies) : null;
    }

    private static ThreadFactory buildPinningDelegate(String namePrefix, AffinityStrategyType[] strategies) {
        try {
            AffinityStrategy[] effective;
            if (strategies == null || strategies.length == 0) {
                effective = new AffinityStrategy[]{AffinityStrategies.ANY};
            } else {
                effective = Arrays.stream(strategies)
                        .map(AffinityStrategyType::toStrategy)
                        .toArray(AffinityStrategy[]::new);
            }
            return new AffinityThreadFactory(namePrefix, effective);
        } catch (Throwable t) {
            log.warn("Affinity pinning requested for '{}' but OpenHFT init failed ({}); "
                    + "falling back to unpinned named threads", namePrefix, t.toString());
            return null;
        }
    }

    @Override
    public Thread newThread(Runnable r) {
        if (delegate != null) {
            Thread pinned = delegate.newThread(r);
            pinned.setDaemon(true);
            return pinned;
        }
        Thread t = new Thread(r, namePrefix + "-" + counter.getAndIncrement());
        t.setDaemon(true);
        return t;
    }

    public boolean isPinningRequested() {
        return pinRequested;
    }

    public boolean isPinningActive() {
        return delegate != null;
    }

    public String getNamePrefix() {
        return namePrefix;
    }
}
