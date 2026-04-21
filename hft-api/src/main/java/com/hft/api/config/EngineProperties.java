package com.hft.api.config;

import com.hft.engine.thread.AffinityStrategyType;
import com.hft.engine.thread.PinnedThreadFactory;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadFactory;

/**
 * Configuration properties for the trading engine's Disruptor ring buffer.
 * Values can be overridden per profile (stub/test/prod) via application-{profile}.properties.
 */
@Component
@ConfigurationProperties(prefix = "hft.engine")
public class EngineProperties {

    private WaitStrategyType waitStrategy = WaitStrategyType.SLEEPING;
    private int ringBufferSize = 65536;
    private boolean pinConsumerThread = false;
    private AffinityStrategyType consumerAffinityStrategy = AffinityStrategyType.ANY;

    public enum WaitStrategyType {
        BUSY_SPIN,
        YIELDING,
        SLEEPING,
        BLOCKING;
    }

    /**
     * Creates the LMAX Disruptor WaitStrategy instance for the configured type.
     */
    public WaitStrategy toWaitStrategy() {
        return switch (waitStrategy) {
            case BUSY_SPIN -> new BusySpinWaitStrategy();
            case YIELDING -> new YieldingWaitStrategy();
            case SLEEPING -> new SleepingWaitStrategy();
            case BLOCKING -> new BlockingWaitStrategy();
        };
    }

    /**
     * Builds the ThreadFactory for the Disruptor consumer, honouring the pin flag.
     * When pinning is off, the factory still produces named daemon threads so thread
     * dumps remain readable.
     */
    public ThreadFactory toConsumerThreadFactory() {
        return new PinnedThreadFactory("disruptor-consumer",
                pinConsumerThread, consumerAffinityStrategy);
    }

    public WaitStrategyType getWaitStrategy() {
        return waitStrategy;
    }

    public void setWaitStrategy(WaitStrategyType waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public void setRingBufferSize(int ringBufferSize) {
        this.ringBufferSize = ringBufferSize;
    }

    public boolean isPinConsumerThread() {
        return pinConsumerThread;
    }

    public void setPinConsumerThread(boolean pinConsumerThread) {
        this.pinConsumerThread = pinConsumerThread;
    }

    public AffinityStrategyType getConsumerAffinityStrategy() {
        return consumerAffinityStrategy;
    }

    public void setConsumerAffinityStrategy(AffinityStrategyType consumerAffinityStrategy) {
        this.consumerAffinityStrategy = consumerAffinityStrategy;
    }
}
