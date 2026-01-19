package com.hft.algo.base;

import com.hft.core.model.OrderSide;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;
import com.hft.core.model.Trade;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract base class for execution algorithms.
 * Provides common functionality for VWAP, TWAP, and similar algorithms.
 */
public abstract class AbstractExecutionAlgorithm implements ExecutionAlgorithm {

    protected final String id;
    protected final Symbol symbol;
    protected final OrderSide side;
    protected final long targetQuantity;
    protected final long limitPrice;
    protected final long startTimeNanos;
    protected final long endTimeNanos;

    protected AlgorithmContext context;
    protected final AtomicReference<AlgorithmState> state = new AtomicReference<>(AlgorithmState.INITIALIZED);

    // Execution tracking
    protected final AtomicLong filledQuantity = new AtomicLong(0);
    protected final AtomicLong totalCost = new AtomicLong(0);
    protected final AtomicLong ordersSubmitted = new AtomicLong(0);
    protected final AtomicLong ordersFilled = new AtomicLong(0);
    protected final AtomicLong ordersCancelled = new AtomicLong(0);
    protected final AtomicLong ordersRejected = new AtomicLong(0);

    protected volatile long actualStartTimeNanos;
    protected volatile long actualEndTimeNanos;
    protected volatile long benchmarkPrice;

    protected AbstractExecutionAlgorithm(
            Symbol symbol,
            OrderSide side,
            long targetQuantity,
            long limitPrice,
            long startTimeNanos,
            long endTimeNanos) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.symbol = symbol;
        this.side = side;
        this.targetQuantity = targetQuantity;
        this.limitPrice = limitPrice;
        this.startTimeNanos = startTimeNanos;
        this.endTimeNanos = endTimeNanos;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Symbol getSymbol() {
        return symbol;
    }

    @Override
    public OrderSide getSide() {
        return side;
    }

    @Override
    public long getTargetQuantity() {
        return targetQuantity;
    }

    @Override
    public long getFilledQuantity() {
        return filledQuantity.get();
    }

    @Override
    public long getAverageFillPrice() {
        long filled = filledQuantity.get();
        if (filled == 0) {
            return 0;
        }
        return totalCost.get() / filled;
    }

    @Override
    public long getLimitPrice() {
        return limitPrice;
    }

    @Override
    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    @Override
    public long getEndTimeNanos() {
        return endTimeNanos;
    }

    @Override
    public AlgorithmState getState() {
        return state.get();
    }

    @Override
    public void initialize(AlgorithmContext context) {
        this.context = context;
        Quote quote = context.getQuote(symbol);
        if (quote != null) {
            // Use mid price as benchmark
            this.benchmarkPrice = (quote.getBidPrice() + quote.getAskPrice()) / 2;
        }
    }

    @Override
    public void start() {
        if (state.compareAndSet(AlgorithmState.INITIALIZED, AlgorithmState.RUNNING) ||
            state.compareAndSet(AlgorithmState.PAUSED, AlgorithmState.RUNNING)) {
            actualStartTimeNanos = context.getCurrentTimeNanos();
            context.logInfo(getName() + " started: " + id);
            onStart();
        }
    }

    @Override
    public void pause() {
        if (state.compareAndSet(AlgorithmState.RUNNING, AlgorithmState.PAUSED)) {
            context.logInfo(getName() + " paused: " + id);
            onPause();
        }
    }

    @Override
    public void resume() {
        if (state.compareAndSet(AlgorithmState.PAUSED, AlgorithmState.RUNNING)) {
            context.logInfo(getName() + " resumed: " + id);
            onResume();
        }
    }

    @Override
    public void cancel() {
        AlgorithmState current = state.get();
        if (current == AlgorithmState.RUNNING || current == AlgorithmState.PAUSED) {
            state.set(AlgorithmState.CANCELLED);
            actualEndTimeNanos = context.getCurrentTimeNanos();
            context.logInfo(getName() + " cancelled: " + id);
            onCancel();
        }
    }

    @Override
    public void onFill(Trade fill) {
        if (!fill.getSymbol().equals(symbol)) {
            return;
        }

        long fillQty = fill.getQuantity();
        long fillPrice = fill.getPrice();

        filledQuantity.addAndGet(fillQty);
        totalCost.addAndGet(fillQty * fillPrice);
        ordersFilled.incrementAndGet();

        context.logInfo(String.format("%s fill: %d @ %d, total filled: %d/%d",
                getName(), fillQty, fillPrice, filledQuantity.get(), targetQuantity));

        if (isComplete()) {
            complete();
        }
    }

    @Override
    public double getProgress() {
        if (targetQuantity == 0) {
            return 100.0;
        }
        return (double) filledQuantity.get() / targetQuantity * 100.0;
    }

    @Override
    public AlgorithmStats getStats() {
        long avgFillPrice = getAverageFillPrice();
        long slippage = 0;
        if (benchmarkPrice > 0 && avgFillPrice > 0) {
            // Slippage in basis points
            slippage = (avgFillPrice - benchmarkPrice) * 10000 / benchmarkPrice;
            if (side == OrderSide.SELL) {
                slippage = -slippage; // For sells, lower fill price is worse
            }
        }

        return AlgorithmStats.builder()
                .algorithmId(id)
                .algorithmName(getName())
                .state(state.get())
                .startTimeNanos(actualStartTimeNanos)
                .endTimeNanos(actualEndTimeNanos)
                .targetQuantity(targetQuantity)
                .filledQuantity(filledQuantity.get())
                .totalOrders(ordersSubmitted.get())
                .filledOrders(ordersFilled.get())
                .cancelledOrders(ordersCancelled.get())
                .rejectedOrders(ordersRejected.get())
                .averageFillPrice(avgFillPrice)
                .benchmarkPrice(benchmarkPrice)
                .slippageBps(slippage)
                .build();
    }

    /**
     * Marks the algorithm as complete.
     */
    protected void complete() {
        if (state.compareAndSet(AlgorithmState.RUNNING, AlgorithmState.COMPLETED)) {
            actualEndTimeNanos = context.getCurrentTimeNanos();
            context.logInfo(String.format("%s completed: %s, filled %d @ avg %d",
                    getName(), id, filledQuantity.get(), getAverageFillPrice()));
            onComplete();
        }
    }

    /**
     * Marks the algorithm as failed.
     */
    protected void fail(String reason) {
        state.set(AlgorithmState.FAILED);
        actualEndTimeNanos = context.getCurrentTimeNanos();
        context.logError(getName() + " failed: " + reason, null);
        onFail(reason);
    }

    /**
     * Submits a child order.
     */
    protected void submitChildOrder(long quantity, long price) {
        if (state.get() != AlgorithmState.RUNNING) {
            return;
        }

        if (quantity <= 0) {
            return;
        }

        // Check limit price constraint
        if (limitPrice > 0) {
            if (side == OrderSide.BUY && price > limitPrice) {
                return; // Don't buy above limit
            }
            if (side == OrderSide.SELL && price < limitPrice) {
                return; // Don't sell below limit
            }
        }

        OrderRequest request = OrderRequest.builder()
                .symbol(symbol)
                .side(side)
                .quantity(quantity)
                .price(price)
                .algorithmId(id)
                .build();

        context.submitOrder(request);
        ordersSubmitted.incrementAndGet();
    }

    /**
     * Gets the elapsed time ratio (0.0 to 1.0).
     */
    protected double getElapsedRatio() {
        long now = context.getCurrentTimeNanos();
        long duration = endTimeNanos - startTimeNanos;
        if (duration <= 0) {
            return 1.0;
        }
        long elapsed = now - startTimeNanos;
        return Math.min(1.0, Math.max(0.0, (double) elapsed / duration));
    }

    /**
     * Gets time remaining in nanoseconds.
     */
    protected long getTimeRemainingNanos() {
        return Math.max(0, endTimeNanos - context.getCurrentTimeNanos());
    }

    // Template methods for subclasses
    protected void onStart() {}
    protected void onPause() {}
    protected void onResume() {}
    protected void onCancel() {}
    protected void onComplete() {}
    protected void onFail(String reason) {}
}
