package com.hft.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // Threshold of 3, cooldown of 100ms
        circuitBreaker = new CircuitBreaker(3, 100);
    }

    @Test
    void shouldStartInClosedState() {
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertNull(circuitBreaker.checkAllowed());
    }

    @Test
    void shouldTripAfterThresholdFailures() {
        circuitBreaker.recordFailure("failure 1");
        circuitBreaker.recordFailure("failure 2");

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        circuitBreaker.recordFailure("failure 3");

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertNotNull(circuitBreaker.checkAllowed());
    }

    @Test
    void shouldResetConsecutiveFailuresOnSuccess() {
        circuitBreaker.recordFailure("failure 1");
        circuitBreaker.recordFailure("failure 2");

        assertEquals(2, circuitBreaker.getConsecutiveFailures());

        circuitBreaker.recordSuccess();

        assertEquals(0, circuitBreaker.getConsecutiveFailures());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void shouldAllowManualTrip() {
        circuitBreaker.trip("Manual trip reason");

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertEquals("Manual trip reason", circuitBreaker.getTripReason());
    }

    @Test
    void shouldAllowManualReset() {
        circuitBreaker.trip("Tripped");
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        circuitBreaker.reset();

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertNull(circuitBreaker.getTripReason());
    }

    @Test
    void shouldTransitionToHalfOpenAfterCooldown() throws InterruptedException {
        circuitBreaker = new CircuitBreaker(1, 50); // 50ms cooldown
        circuitBreaker.trip("Test");

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertNotNull(circuitBreaker.checkAllowed());

        Thread.sleep(60); // Wait for cooldown

        assertNull(circuitBreaker.checkAllowed()); // Should be allowed now
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
    }

    @Test
    void shouldCloseOnSuccessInHalfOpenState() throws InterruptedException {
        circuitBreaker = new CircuitBreaker(1, 50);
        circuitBreaker.trip("Test");

        Thread.sleep(60);
        circuitBreaker.checkAllowed(); // Transition to HALF_OPEN

        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());

        circuitBreaker.recordSuccess();

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void shouldTripImmediatelyOnFailureInHalfOpenState() throws InterruptedException {
        circuitBreaker = new CircuitBreaker(1, 50);
        circuitBreaker.trip("Test");

        Thread.sleep(60);
        circuitBreaker.checkAllowed(); // Transition to HALF_OPEN

        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());

        circuitBreaker.recordFailure("Half-open failure");

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void shouldTrackRemainingCooldown() {
        circuitBreaker = new CircuitBreaker(1, 1000);

        assertEquals(0, circuitBreaker.getRemainingCooldownMs());

        circuitBreaker.trip("Test");

        assertTrue(circuitBreaker.getRemainingCooldownMs() > 900);
        assertTrue(circuitBreaker.getRemainingCooldownMs() <= 1000);
    }
}
