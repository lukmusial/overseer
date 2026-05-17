package com.hft.api.config;

import com.hft.engine.thread.AffinityStrategyType;
import com.hft.engine.thread.PinnedThreadFactory;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class EnginePropertiesTest {

    @Test
    void defaultsToSleepingWaitStrategy() {
        var props = new EngineProperties();
        assertEquals(EngineProperties.WaitStrategyType.SLEEPING, props.getWaitStrategy());
        assertInstanceOf(SleepingWaitStrategy.class, props.toWaitStrategy());
    }

    @Test
    void defaultRingBufferSize() {
        var props = new EngineProperties();
        assertEquals(65536, props.getRingBufferSize());
    }

    @Test
    void busySpinMapsCorrectly() {
        var props = new EngineProperties();
        props.setWaitStrategy(EngineProperties.WaitStrategyType.BUSY_SPIN);
        assertInstanceOf(BusySpinWaitStrategy.class, props.toWaitStrategy());
    }

    @Test
    void yieldingMapsCorrectly() {
        var props = new EngineProperties();
        props.setWaitStrategy(EngineProperties.WaitStrategyType.YIELDING);
        assertInstanceOf(YieldingWaitStrategy.class, props.toWaitStrategy());
    }

    @Test
    void sleepingMapsCorrectly() {
        var props = new EngineProperties();
        props.setWaitStrategy(EngineProperties.WaitStrategyType.SLEEPING);
        assertInstanceOf(SleepingWaitStrategy.class, props.toWaitStrategy());
    }

    @Test
    void blockingMapsCorrectly() {
        var props = new EngineProperties();
        props.setWaitStrategy(EngineProperties.WaitStrategyType.BLOCKING);
        assertInstanceOf(BlockingWaitStrategy.class, props.toWaitStrategy());
    }

    @ParameterizedTest
    @EnumSource(EngineProperties.WaitStrategyType.class)
    void allEnumValuesProduceNonNullStrategy(EngineProperties.WaitStrategyType type) {
        var props = new EngineProperties();
        props.setWaitStrategy(type);
        assertNotNull(props.toWaitStrategy());
    }

    @Test
    void setRingBufferSize() {
        var props = new EngineProperties();
        props.setRingBufferSize(1024);
        assertEquals(1024, props.getRingBufferSize());
    }

    @Test
    void pinConsumerThreadDefaultsOff() {
        var props = new EngineProperties();
        assertFalse(props.isPinConsumerThread());
        assertEquals(AffinityStrategyType.ANY, props.getConsumerAffinityStrategy());
    }

    @Test
    void threadFactoryReflectsPinSetting() {
        var off = new EngineProperties();
        var factoryOff = (PinnedThreadFactory) off.toConsumerThreadFactory();
        assertFalse(factoryOff.isPinningRequested());
        assertFalse(factoryOff.isPinningActive());

        var on = new EngineProperties();
        on.setPinConsumerThread(true);
        on.setConsumerAffinityStrategy(AffinityStrategyType.SAME_CORE);
        var factoryOn = (PinnedThreadFactory) on.toConsumerThreadFactory();
        assertTrue(factoryOn.isPinningRequested());
        // isPinningActive depends on platform: true on Linux, may be false elsewhere
        // if the AffinityThreadFactory initializer fails — we just assert the request was honoured.
    }
}
