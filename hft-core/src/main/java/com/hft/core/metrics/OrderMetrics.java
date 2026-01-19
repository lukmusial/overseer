package com.hft.core.metrics;

import org.agrona.collections.Long2LongHashMap;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Comprehensive order metrics tracking for performance analysis.
 * Thread-safe using atomic operations.
 */
public class OrderMetrics {
    // Order counts
    private final LongAdder ordersSubmitted = new LongAdder();
    private final LongAdder ordersAccepted = new LongAdder();
    private final LongAdder ordersFilled = new LongAdder();
    private final LongAdder ordersPartiallyFilled = new LongAdder();
    private final LongAdder ordersCancelled = new LongAdder();
    private final LongAdder ordersRejected = new LongAdder();

    // Latency tracking (in nanoseconds)
    private final LatencyHistogram submitLatency = new LatencyHistogram();
    private final LatencyHistogram ackLatency = new LatencyHistogram();
    private final LatencyHistogram fillLatency = new LatencyHistogram();
    private final LatencyHistogram cancelLatency = new LatencyHistogram();
    private final LatencyHistogram roundTripLatency = new LatencyHistogram();

    // Volume metrics
    private final LongAdder totalQuantityOrdered = new LongAdder();
    private final LongAdder totalQuantityFilled = new LongAdder();
    private final LongAdder totalNotionalValue = new LongAdder();

    // Error tracking
    private final LongAdder connectionErrors = new LongAdder();
    private final LongAdder timeoutErrors = new LongAdder();
    private final LongAdder validationErrors = new LongAdder();

    // Throughput tracking
    private final AtomicLong windowStartNanos = new AtomicLong(System.nanoTime());
    private final LongAdder ordersInWindow = new LongAdder();
    private volatile double currentThroughput = 0.0;

    public void recordOrderSubmitted() {
        ordersSubmitted.increment();
        ordersInWindow.increment();
        updateThroughput();
    }

    public void recordOrderAccepted(long latencyNanos) {
        ordersAccepted.increment();
        ackLatency.record(latencyNanos);
    }

    public void recordOrderFilled(long latencyNanos, long quantity, long notionalValue) {
        ordersFilled.increment();
        fillLatency.record(latencyNanos);
        totalQuantityFilled.add(quantity);
        totalNotionalValue.add(notionalValue);
    }

    public void recordOrderPartiallyFilled(long quantity) {
        ordersPartiallyFilled.increment();
        totalQuantityFilled.add(quantity);
    }

    public void recordOrderCancelled(long latencyNanos) {
        ordersCancelled.increment();
        cancelLatency.record(latencyNanos);
    }

    public void recordOrderRejected() {
        ordersRejected.increment();
    }

    public void recordSubmitLatency(long latencyNanos) {
        submitLatency.record(latencyNanos);
    }

    public void recordRoundTripLatency(long latencyNanos) {
        roundTripLatency.record(latencyNanos);
    }

    public void recordQuantityOrdered(long quantity) {
        totalQuantityOrdered.add(quantity);
    }

    public void recordConnectionError() {
        connectionErrors.increment();
    }

    public void recordTimeoutError() {
        timeoutErrors.increment();
    }

    public void recordValidationError() {
        validationErrors.increment();
    }

    private void updateThroughput() {
        long now = System.nanoTime();
        long windowStart = windowStartNanos.get();
        long elapsed = now - windowStart;

        // Update throughput every second
        if (elapsed >= 1_000_000_000L) {
            if (windowStartNanos.compareAndSet(windowStart, now)) {
                long orders = ordersInWindow.sumThenReset();
                currentThroughput = orders * 1_000_000_000.0 / elapsed;
            }
        }
    }

    // Getters for metrics
    public long getOrdersSubmitted() {
        return ordersSubmitted.sum();
    }

    public long getOrdersAccepted() {
        return ordersAccepted.sum();
    }

    public long getOrdersFilled() {
        return ordersFilled.sum();
    }

    public long getOrdersPartiallyFilled() {
        return ordersPartiallyFilled.sum();
    }

    public long getOrdersCancelled() {
        return ordersCancelled.sum();
    }

    public long getOrdersRejected() {
        return ordersRejected.sum();
    }

    public long getTotalQuantityOrdered() {
        return totalQuantityOrdered.sum();
    }

    public long getTotalQuantityFilled() {
        return totalQuantityFilled.sum();
    }

    public long getTotalNotionalValue() {
        return totalNotionalValue.sum();
    }

    public double getFillRate() {
        long submitted = ordersSubmitted.sum();
        return submitted > 0 ? (double) ordersFilled.sum() / submitted : 0.0;
    }

    public double getRejectRate() {
        long submitted = ordersSubmitted.sum();
        return submitted > 0 ? (double) ordersRejected.sum() / submitted : 0.0;
    }

    public double getCurrentThroughput() {
        return currentThroughput;
    }

    public LatencyStats getSubmitLatencyStats() {
        return submitLatency.getStats();
    }

    public LatencyStats getAckLatencyStats() {
        return ackLatency.getStats();
    }

    public LatencyStats getFillLatencyStats() {
        return fillLatency.getStats();
    }

    public LatencyStats getCancelLatencyStats() {
        return cancelLatency.getStats();
    }

    public LatencyStats getRoundTripLatencyStats() {
        return roundTripLatency.getStats();
    }

    public long getConnectionErrors() {
        return connectionErrors.sum();
    }

    public long getTimeoutErrors() {
        return timeoutErrors.sum();
    }

    public long getValidationErrors() {
        return validationErrors.sum();
    }

    public void reset() {
        ordersSubmitted.reset();
        ordersAccepted.reset();
        ordersFilled.reset();
        ordersPartiallyFilled.reset();
        ordersCancelled.reset();
        ordersRejected.reset();
        submitLatency.reset();
        ackLatency.reset();
        fillLatency.reset();
        cancelLatency.reset();
        roundTripLatency.reset();
        totalQuantityOrdered.reset();
        totalQuantityFilled.reset();
        totalNotionalValue.reset();
        connectionErrors.reset();
        timeoutErrors.reset();
        validationErrors.reset();
        ordersInWindow.reset();
        windowStartNanos.set(System.nanoTime());
        currentThroughput = 0.0;
    }

    public MetricsSnapshot snapshot() {
        return new MetricsSnapshot(
                getOrdersSubmitted(),
                getOrdersAccepted(),
                getOrdersFilled(),
                getOrdersPartiallyFilled(),
                getOrdersCancelled(),
                getOrdersRejected(),
                getTotalQuantityOrdered(),
                getTotalQuantityFilled(),
                getTotalNotionalValue(),
                getFillRate(),
                getRejectRate(),
                getCurrentThroughput(),
                getSubmitLatencyStats(),
                getAckLatencyStats(),
                getFillLatencyStats(),
                getCancelLatencyStats(),
                getRoundTripLatencyStats(),
                getConnectionErrors(),
                getTimeoutErrors(),
                getValidationErrors()
        );
    }

    /**
     * Immutable snapshot of all metrics at a point in time.
     */
    public record MetricsSnapshot(
            long ordersSubmitted,
            long ordersAccepted,
            long ordersFilled,
            long ordersPartiallyFilled,
            long ordersCancelled,
            long ordersRejected,
            long totalQuantityOrdered,
            long totalQuantityFilled,
            long totalNotionalValue,
            double fillRate,
            double rejectRate,
            double throughputPerSecond,
            LatencyStats submitLatency,
            LatencyStats ackLatency,
            LatencyStats fillLatency,
            LatencyStats cancelLatency,
            LatencyStats roundTripLatency,
            long connectionErrors,
            long timeoutErrors,
            long validationErrors
    ) {}
}
