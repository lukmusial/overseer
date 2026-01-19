package com.hft.core.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderMetricsTest {
    private OrderMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new OrderMetrics();
    }

    @Test
    void shouldStartWithZeroCounts() {
        assertEquals(0, metrics.getOrdersSubmitted());
        assertEquals(0, metrics.getOrdersAccepted());
        assertEquals(0, metrics.getOrdersFilled());
        assertEquals(0, metrics.getOrdersCancelled());
        assertEquals(0, metrics.getOrdersRejected());
    }

    @Test
    void shouldTrackOrderSubmissions() {
        metrics.recordOrderSubmitted();
        metrics.recordOrderSubmitted();
        metrics.recordOrderSubmitted();

        assertEquals(3, metrics.getOrdersSubmitted());
    }

    @Test
    void shouldTrackOrderAcceptances() {
        metrics.recordOrderAccepted(1000);
        metrics.recordOrderAccepted(2000);

        assertEquals(2, metrics.getOrdersAccepted());

        LatencyStats ackLatency = metrics.getAckLatencyStats();
        assertEquals(2, ackLatency.count());
    }

    @Test
    void shouldTrackOrderFills() {
        metrics.recordOrderFilled(5000, 100, 150000); // 5us latency, 100 qty, $1500 notional
        metrics.recordOrderFilled(3000, 50, 75000);

        assertEquals(2, metrics.getOrdersFilled());
        assertEquals(150, metrics.getTotalQuantityFilled());
        assertEquals(225000, metrics.getTotalNotionalValue());
    }

    @Test
    void shouldCalculateFillRate() {
        metrics.recordOrderSubmitted();
        metrics.recordOrderSubmitted();
        metrics.recordOrderSubmitted();
        metrics.recordOrderSubmitted();

        metrics.recordOrderFilled(1000, 100, 10000);
        metrics.recordOrderFilled(1000, 100, 10000);

        assertEquals(0.5, metrics.getFillRate(), 0.01);
    }

    @Test
    void shouldCalculateRejectRate() {
        metrics.recordOrderSubmitted();
        metrics.recordOrderSubmitted();
        metrics.recordOrderSubmitted();
        metrics.recordOrderSubmitted();

        metrics.recordOrderRejected();

        assertEquals(0.25, metrics.getRejectRate(), 0.01);
    }

    @Test
    void shouldTrackErrors() {
        metrics.recordConnectionError();
        metrics.recordConnectionError();
        metrics.recordTimeoutError();
        metrics.recordValidationError();
        metrics.recordValidationError();
        metrics.recordValidationError();

        assertEquals(2, metrics.getConnectionErrors());
        assertEquals(1, metrics.getTimeoutErrors());
        assertEquals(3, metrics.getValidationErrors());
    }

    @Test
    void shouldTrackSubmitLatency() {
        metrics.recordSubmitLatency(1000);
        metrics.recordSubmitLatency(2000);
        metrics.recordSubmitLatency(3000);

        LatencyStats stats = metrics.getSubmitLatencyStats();
        assertEquals(3, stats.count());
        assertEquals(2000, stats.mean());
    }

    @Test
    void shouldTrackCancelLatency() {
        metrics.recordOrderCancelled(5000);
        metrics.recordOrderCancelled(3000);

        assertEquals(2, metrics.getOrdersCancelled());

        LatencyStats stats = metrics.getCancelLatencyStats();
        assertEquals(2, stats.count());
    }

    @Test
    void shouldTrackRoundTripLatency() {
        metrics.recordRoundTripLatency(10000);
        metrics.recordRoundTripLatency(15000);
        metrics.recordRoundTripLatency(12000);

        LatencyStats stats = metrics.getRoundTripLatencyStats();
        assertEquals(3, stats.count());
        assertTrue(stats.mean() > 10000);
    }

    @Test
    void shouldReset() {
        metrics.recordOrderSubmitted();
        metrics.recordOrderFilled(1000, 100, 10000);
        metrics.recordConnectionError();

        metrics.reset();

        assertEquals(0, metrics.getOrdersSubmitted());
        assertEquals(0, metrics.getOrdersFilled());
        assertEquals(0, metrics.getConnectionErrors());
    }

    @Test
    void shouldCreateSnapshot() {
        metrics.recordOrderSubmitted();
        metrics.recordOrderSubmitted();
        metrics.recordOrderAccepted(1000);
        metrics.recordOrderFilled(2000, 100, 15000);
        metrics.recordOrderRejected();

        OrderMetrics.MetricsSnapshot snapshot = metrics.snapshot();

        assertEquals(2, snapshot.ordersSubmitted());
        assertEquals(1, snapshot.ordersAccepted());
        assertEquals(1, snapshot.ordersFilled());
        assertEquals(1, snapshot.ordersRejected());
        assertEquals(100, snapshot.totalQuantityFilled());
        assertEquals(15000, snapshot.totalNotionalValue());
        assertEquals(0.5, snapshot.fillRate(), 0.01);
        assertEquals(0.5, snapshot.rejectRate(), 0.01);
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        int threadCount = 4;
        int operationsPerThread = 1000;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    metrics.recordOrderSubmitted();
                    metrics.recordSubmitLatency(1000 + j);
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(threadCount * operationsPerThread, metrics.getOrdersSubmitted());
    }
}
