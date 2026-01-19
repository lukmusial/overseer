package com.hft.algo.execution;

import com.hft.algo.base.AbstractExecutionAlgorithm;
import com.hft.algo.base.AlgorithmContext;
import com.hft.algo.base.AlgorithmState;
import com.hft.core.model.OrderSide;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;

/**
 * Volume-Weighted Average Price (VWAP) execution algorithm.
 *
 * VWAP executes orders in proportion to historical volume patterns,
 * aiming to achieve an average execution price close to the market VWAP.
 *
 * Key features:
 * - Slices orders based on historical volume profile
 * - Dynamically adjusts pace based on actual vs expected fills
 * - Supports participation rate limits
 * - Handles market impact by spreading orders
 */
public class VwapAlgorithm extends AbstractExecutionAlgorithm {

    private static final String NAME = "VWAP";
    private static final int DEFAULT_BUCKETS = 10;

    // Volume profile
    private long[] volumeProfile;
    private long totalHistoricalVolume;

    // Participation constraints
    private final double maxParticipationRate;

    // Execution schedule
    private long[] scheduledQuantities;
    private long[] executedQuantities;
    private int currentBucket;
    private long bucketDurationNanos;

    // Catch-up tracking
    private long behindSchedule;

    public VwapAlgorithm(
            Symbol symbol,
            OrderSide side,
            long targetQuantity,
            long limitPrice,
            long startTimeNanos,
            long endTimeNanos,
            double maxParticipationRate) {
        super(symbol, side, targetQuantity, limitPrice, startTimeNanos, endTimeNanos);
        this.maxParticipationRate = maxParticipationRate > 0 ? maxParticipationRate : 0.25; // Default 25%
    }

    public VwapAlgorithm(
            Symbol symbol,
            OrderSide side,
            long targetQuantity,
            long limitPrice,
            long startTimeNanos,
            long endTimeNanos) {
        this(symbol, side, targetQuantity, limitPrice, startTimeNanos, endTimeNanos, 0.25);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AlgorithmContext context) {
        super.initialize(context);

        // Get historical volume profile
        this.volumeProfile = context.getHistoricalVolume(symbol, DEFAULT_BUCKETS);
        if (volumeProfile == null || volumeProfile.length == 0) {
            // Use uniform distribution if no historical data
            volumeProfile = new long[DEFAULT_BUCKETS];
            for (int i = 0; i < DEFAULT_BUCKETS; i++) {
                volumeProfile[i] = 1;
            }
        }

        // Calculate total historical volume
        totalHistoricalVolume = 0;
        for (long v : volumeProfile) {
            totalHistoricalVolume += v;
        }

        // Calculate scheduled quantities for each bucket
        int buckets = volumeProfile.length;
        scheduledQuantities = new long[buckets];
        executedQuantities = new long[buckets];

        long totalScheduled = 0;
        for (int i = 0; i < buckets - 1; i++) {
            scheduledQuantities[i] = (long) ((double) volumeProfile[i] / totalHistoricalVolume * targetQuantity);
            totalScheduled += scheduledQuantities[i];
        }
        // Put remainder in last bucket to ensure we hit target exactly
        scheduledQuantities[buckets - 1] = targetQuantity - totalScheduled;

        // Calculate bucket duration
        long totalDuration = endTimeNanos - startTimeNanos;
        bucketDurationNanos = totalDuration / buckets;

        currentBucket = 0;
        behindSchedule = 0;

        context.logInfo(String.format("VWAP initialized: %d shares over %d buckets, max participation %.1f%%",
                targetQuantity, buckets, maxParticipationRate * 100));
    }

    @Override
    public void onQuote(Quote quote) {
        if (state.get() != AlgorithmState.RUNNING) {
            return;
        }

        if (!quote.getSymbol().equals(symbol)) {
            return;
        }

        // Calculate how much we should have executed by now
        int expectedBucket = getCurrentBucket();
        long expectedFilled = getExpectedFilled(expectedBucket);
        long actualFilled = filledQuantity.get();

        // Track if we're behind schedule
        behindSchedule = Math.max(0, expectedFilled - actualFilled);

        // Determine order quantity
        long remaining = getRemainingQuantity();
        if (remaining <= 0) {
            return;
        }

        // Calculate quantity for this opportunity
        long targetForBucket = getTargetForCurrentBucket();
        long executedThisBucket = executedQuantities[Math.min(expectedBucket, executedQuantities.length - 1)];
        long bucketRemaining = Math.max(0, targetForBucket - executedThisBucket);

        // Add catch-up quantity if behind schedule (spread over remaining buckets)
        int bucketsRemaining = volumeProfile.length - expectedBucket;
        long catchupQty = bucketsRemaining > 0 ? behindSchedule / bucketsRemaining : behindSchedule;

        long orderQty = Math.min(remaining, bucketRemaining + catchupQty);

        // Apply participation rate limit based on displayed liquidity
        long availableLiquidity = side == OrderSide.BUY ? quote.getAskSize() : quote.getBidSize();
        long maxByParticipation = (long) (availableLiquidity * maxParticipationRate);
        orderQty = Math.min(orderQty, Math.max(1, maxByParticipation));

        // Minimum order size
        if (orderQty < 1) {
            return;
        }

        // Determine price
        long price = side == OrderSide.BUY ? quote.getAskPrice() : quote.getBidPrice();

        // Submit order
        submitChildOrder(orderQty, price);
    }

    @Override
    public void onTimer(long timestampNanos) {
        if (state.get() != AlgorithmState.RUNNING) {
            return;
        }

        // Check if we've passed the deadline
        if (timestampNanos >= endTimeNanos) {
            if (getRemainingQuantity() > 0) {
                // Execution incomplete at deadline
                context.logInfo(String.format("VWAP deadline reached: filled %d/%d (%.1f%%)",
                        filledQuantity.get(), targetQuantity, getProgress()));
            }
            complete();
            return;
        }

        // Update current bucket
        int newBucket = getCurrentBucket();
        if (newBucket != currentBucket) {
            // Log bucket transition
            long executedInOldBucket = executedQuantities[currentBucket];
            long targetInOldBucket = scheduledQuantities[currentBucket];
            context.logInfo(String.format("VWAP bucket %d complete: executed %d/%d",
                    currentBucket, executedInOldBucket, targetInOldBucket));
            currentBucket = newBucket;
        }
    }

    /**
     * Gets the current time bucket index.
     */
    private int getCurrentBucket() {
        if (bucketDurationNanos <= 0) {
            return 0;
        }
        long elapsed = context.getCurrentTimeNanos() - startTimeNanos;
        int bucket = (int) (elapsed / bucketDurationNanos);
        return Math.min(bucket, volumeProfile.length - 1);
    }

    /**
     * Gets expected filled quantity by a given bucket.
     */
    private long getExpectedFilled(int bucket) {
        long expected = 0;
        for (int i = 0; i <= bucket && i < scheduledQuantities.length; i++) {
            expected += scheduledQuantities[i];
        }
        return expected;
    }

    /**
     * Gets target quantity for current bucket including catch-up.
     */
    private long getTargetForCurrentBucket() {
        int bucket = getCurrentBucket();
        if (bucket >= scheduledQuantities.length) {
            return 0;
        }
        return scheduledQuantities[bucket];
    }

    @Override
    public void onFill(com.hft.core.model.Trade fill) {
        super.onFill(fill);

        // Track execution by bucket
        int bucket = getCurrentBucket();
        if (bucket < executedQuantities.length) {
            executedQuantities[bucket] += fill.getQuantity();
        }
    }

    /**
     * Creates a builder for VwapAlgorithm.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Symbol symbol;
        private OrderSide side;
        private long targetQuantity;
        private long limitPrice;
        private long startTimeNanos;
        private long endTimeNanos;
        private double maxParticipationRate = 0.25;

        public Builder symbol(Symbol symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder side(OrderSide side) {
            this.side = side;
            return this;
        }

        public Builder targetQuantity(long targetQuantity) {
            this.targetQuantity = targetQuantity;
            return this;
        }

        public Builder limitPrice(long limitPrice) {
            this.limitPrice = limitPrice;
            return this;
        }

        public Builder startTimeNanos(long startTimeNanos) {
            this.startTimeNanos = startTimeNanos;
            return this;
        }

        public Builder endTimeNanos(long endTimeNanos) {
            this.endTimeNanos = endTimeNanos;
            return this;
        }

        public Builder maxParticipationRate(double maxParticipationRate) {
            this.maxParticipationRate = maxParticipationRate;
            return this;
        }

        public Builder duration(long durationNanos) {
            this.endTimeNanos = this.startTimeNanos + durationNanos;
            return this;
        }

        public VwapAlgorithm build() {
            if (symbol == null) throw new IllegalStateException("Symbol is required");
            if (side == null) throw new IllegalStateException("Side is required");
            if (targetQuantity <= 0) throw new IllegalStateException("Target quantity must be positive");
            if (endTimeNanos <= startTimeNanos) throw new IllegalStateException("End time must be after start time");

            return new VwapAlgorithm(symbol, side, targetQuantity, limitPrice,
                    startTimeNanos, endTimeNanos, maxParticipationRate);
        }
    }
}
