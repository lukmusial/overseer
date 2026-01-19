package com.hft.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit breaker for risk management.
 * Trips after a threshold of rejections and requires cooldown period.
 */
public class CircuitBreaker {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State {
        CLOSED,     // Normal operation
        OPEN,       // Tripped, rejecting all
        HALF_OPEN   // Testing if safe to resume
    }

    private final int threshold;
    private final long cooldownMs;

    private volatile State state = State.CLOSED;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastTripTime = new AtomicLong(0);
    private volatile String tripReason;

    public CircuitBreaker(int threshold, long cooldownMs) {
        this.threshold = threshold;
        this.cooldownMs = cooldownMs;
    }

    /**
     * Checks if the circuit breaker allows trading.
     *
     * @return null if allowed, reason string if blocked
     */
    public String checkAllowed() {
        switch (state) {
            case CLOSED:
                return null;

            case OPEN:
                if (System.currentTimeMillis() - lastTripTime.get() > cooldownMs) {
                    state = State.HALF_OPEN;
                    log.info("Circuit breaker entering half-open state");
                    return null;
                }
                return "Circuit breaker tripped: " + tripReason;

            case HALF_OPEN:
                return null;

            default:
                return null;
        }
    }

    /**
     * Records a successful operation.
     */
    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            reset();
            log.info("Circuit breaker closed after successful operation");
        }
        consecutiveFailures.set(0);
    }

    /**
     * Records a failure/rejection.
     */
    public void recordFailure(String reason) {
        int failures = consecutiveFailures.incrementAndGet();

        if (state == State.HALF_OPEN) {
            trip(reason);
            return;
        }

        if (failures >= threshold) {
            trip(reason);
        }
    }

    /**
     * Manually trips the circuit breaker.
     */
    public void trip(String reason) {
        if (state != State.OPEN) {
            state = State.OPEN;
            lastTripTime.set(System.currentTimeMillis());
            tripReason = reason;
            log.error("Circuit breaker tripped: {}", reason);
        }
    }

    /**
     * Manually resets the circuit breaker.
     */
    public void reset() {
        state = State.CLOSED;
        consecutiveFailures.set(0);
        tripReason = null;
        log.info("Circuit breaker reset");
    }

    public State getState() {
        return state;
    }

    public String getTripReason() {
        return tripReason;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public long getLastTripTime() {
        return lastTripTime.get();
    }

    public long getRemainingCooldownMs() {
        if (state != State.OPEN) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastTripTime.get();
        return Math.max(0, cooldownMs - elapsed);
    }
}
