package com.hft.algo.execution;

import com.hft.algo.base.AbstractExecutionAlgorithm;
import com.hft.algo.base.AlgorithmContext;
import com.hft.algo.base.AlgorithmState;
import com.hft.core.model.OrderSide;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;

/**
 * Time-Weighted Average Price (TWAP) execution algorithm.
 *
 * TWAP executes orders in equal slices over time, aiming to achieve
 * an average execution price close to the time-weighted market price.
 *
 * Key features:
 * - Uniform order distribution over time
 * - Simple and predictable execution pattern
 * - Automatic catch-up for missed fills
 * - Configurable slice interval and participation rate
 */
public class TwapAlgorithm extends AbstractExecutionAlgorithm {

    private static final String NAME = "TWAP";
    private static final long DEFAULT_SLICE_INTERVAL_NANOS = 60_000_000_000L; // 1 minute

    // Execution parameters
    private final long sliceIntervalNanos;
    private final double maxParticipationRate;

    // Slice tracking
    private int totalSlices;
    private long quantityPerSlice;
    private int currentSlice;
    private long lastSliceTime;
    private long executedThisSlice;

    public TwapAlgorithm(
            Symbol symbol,
            OrderSide side,
            long targetQuantity,
            long limitPrice,
            long startTimeNanos,
            long endTimeNanos,
            long sliceIntervalNanos,
            double maxParticipationRate) {
        super(symbol, side, targetQuantity, limitPrice, startTimeNanos, endTimeNanos);
        this.sliceIntervalNanos = sliceIntervalNanos > 0 ? sliceIntervalNanos : DEFAULT_SLICE_INTERVAL_NANOS;
        this.maxParticipationRate = maxParticipationRate > 0 ? maxParticipationRate : 0.25;
    }

    public TwapAlgorithm(
            Symbol symbol,
            OrderSide side,
            long targetQuantity,
            long limitPrice,
            long startTimeNanos,
            long endTimeNanos) {
        this(symbol, side, targetQuantity, limitPrice, startTimeNanos, endTimeNanos,
                DEFAULT_SLICE_INTERVAL_NANOS, 0.25);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initialize(AlgorithmContext context) {
        super.initialize(context);

        // Calculate number of slices
        long totalDuration = endTimeNanos - startTimeNanos;
        this.totalSlices = Math.max(1, (int) (totalDuration / sliceIntervalNanos));

        // Calculate quantity per slice
        this.quantityPerSlice = targetQuantity / totalSlices;

        // Handle remainder by distributing across first slices
        long remainder = targetQuantity - (quantityPerSlice * totalSlices);

        this.currentSlice = 0;
        this.lastSliceTime = startTimeNanos;
        this.executedThisSlice = 0;

        context.logInfo(String.format("TWAP initialized: %d shares in %d slices of %d each, interval %.1fs",
                targetQuantity, totalSlices, quantityPerSlice, sliceIntervalNanos / 1_000_000_000.0));
    }

    @Override
    public void onQuote(Quote quote) {
        if (state.get() != AlgorithmState.RUNNING) {
            return;
        }

        if (!quote.getSymbol().equals(symbol)) {
            return;
        }

        long remaining = getRemainingQuantity();
        if (remaining <= 0) {
            return;
        }

        // Calculate target for current slice
        long targetForSlice = getTargetForCurrentSlice();
        long sliceRemaining = Math.max(0, targetForSlice - executedThisSlice);

        // Add catch-up for behind schedule
        long behindSchedule = getExpectedFilled() - filledQuantity.get();
        int slicesRemaining = totalSlices - currentSlice;
        long catchupQty = slicesRemaining > 0 ? Math.max(0, behindSchedule) / slicesRemaining : behindSchedule;

        long orderQty = Math.min(remaining, sliceRemaining + catchupQty);

        // Apply participation rate limit
        long availableLiquidity = side == OrderSide.BUY ? quote.getAskSize() : quote.getBidSize();
        long maxByParticipation = (long) (availableLiquidity * maxParticipationRate);
        orderQty = Math.min(orderQty, Math.max(1, maxByParticipation));

        if (orderQty < 1) {
            return;
        }

        // Determine price
        long price = side == OrderSide.BUY ? quote.getAskPrice() : quote.getBidPrice();

        submitChildOrder(orderQty, price);
    }

    @Override
    public void onTimer(long timestampNanos) {
        if (state.get() != AlgorithmState.RUNNING) {
            return;
        }

        // Check deadline
        if (timestampNanos >= endTimeNanos) {
            if (getRemainingQuantity() > 0) {
                context.logInfo(String.format("TWAP deadline reached: filled %d/%d (%.1f%%)",
                        filledQuantity.get(), targetQuantity, getProgress()));
            }
            complete();
            return;
        }

        // Check for slice transition
        int newSlice = getCurrentSlice(timestampNanos);
        if (newSlice != currentSlice) {
            context.logInfo(String.format("TWAP slice %d complete: executed %d/%d, total %d/%d",
                    currentSlice, executedThisSlice, getTargetForSlice(currentSlice),
                    filledQuantity.get(), targetQuantity));
            currentSlice = newSlice;
            executedThisSlice = 0;
            lastSliceTime = timestampNanos;
        }
    }

    @Override
    public void onFill(com.hft.core.model.Trade fill) {
        super.onFill(fill);
        executedThisSlice += fill.getQuantity();
    }

    /**
     * Gets current slice index based on time.
     */
    private int getCurrentSlice(long timestampNanos) {
        long elapsed = timestampNanos - startTimeNanos;
        int slice = (int) (elapsed / sliceIntervalNanos);
        return Math.min(slice, totalSlices - 1);
    }

    /**
     * Gets target quantity for a specific slice.
     */
    private long getTargetForSlice(int slice) {
        if (slice < 0 || slice >= totalSlices) {
            return 0;
        }

        // Base quantity plus remainder distribution
        long target = quantityPerSlice;
        long remainder = targetQuantity - (quantityPerSlice * totalSlices);
        if (slice < remainder) {
            target++; // Distribute remainder across first slices
        }
        return target;
    }

    /**
     * Gets target quantity for current slice.
     */
    private long getTargetForCurrentSlice() {
        return getTargetForSlice(currentSlice);
    }

    /**
     * Gets expected filled quantity by now.
     */
    private long getExpectedFilled() {
        long expected = 0;
        for (int i = 0; i <= currentSlice && i < totalSlices; i++) {
            expected += getTargetForSlice(i);
        }
        return expected;
    }

    /**
     * Creates a builder for TwapAlgorithm.
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
        private long sliceIntervalNanos = DEFAULT_SLICE_INTERVAL_NANOS;
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

        public Builder duration(long durationNanos) {
            this.endTimeNanos = this.startTimeNanos + durationNanos;
            return this;
        }

        public Builder sliceIntervalNanos(long sliceIntervalNanos) {
            this.sliceIntervalNanos = sliceIntervalNanos;
            return this;
        }

        public Builder sliceIntervalSeconds(int seconds) {
            this.sliceIntervalNanos = seconds * 1_000_000_000L;
            return this;
        }

        public Builder maxParticipationRate(double maxParticipationRate) {
            this.maxParticipationRate = maxParticipationRate;
            return this;
        }

        public TwapAlgorithm build() {
            if (symbol == null) throw new IllegalStateException("Symbol is required");
            if (side == null) throw new IllegalStateException("Side is required");
            if (targetQuantity <= 0) throw new IllegalStateException("Target quantity must be positive");
            if (endTimeNanos <= startTimeNanos) throw new IllegalStateException("End time must be after start time");

            return new TwapAlgorithm(symbol, side, targetQuantity, limitPrice,
                    startTimeNanos, endTimeNanos, sliceIntervalNanos, maxParticipationRate);
        }
    }
}
