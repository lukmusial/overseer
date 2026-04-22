package com.hft.engine.thread;

import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityStrategy;

/**
 * Subset of OpenHFT affinity strategies that are meaningful for a single Disruptor
 * consumer thread. Kept in hft-engine so upstream modules (hft-api config) can bind
 * to this enum by name without taking a direct dependency on OpenHFT.
 */
public enum AffinityStrategyType {
    ANY,
    SAME_CORE,
    SAME_SOCKET,
    DIFFERENT_CORE,
    DIFFERENT_SOCKET;

    public AffinityStrategy toStrategy() {
        return switch (this) {
            case ANY -> AffinityStrategies.ANY;
            case SAME_CORE -> AffinityStrategies.SAME_CORE;
            case SAME_SOCKET -> AffinityStrategies.SAME_SOCKET;
            case DIFFERENT_CORE -> AffinityStrategies.DIFFERENT_CORE;
            case DIFFERENT_SOCKET -> AffinityStrategies.DIFFERENT_SOCKET;
        };
    }
}
