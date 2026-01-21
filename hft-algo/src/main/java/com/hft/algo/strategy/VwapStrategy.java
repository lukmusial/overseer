package com.hft.algo.strategy;

import com.hft.algo.base.AbstractTradingStrategy;
import com.hft.algo.base.StrategyParameters;
import com.hft.core.model.OrderSide;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;

import java.util.*;

/**
 * VWAP (Volume-Weighted Average Price) trading strategy.
 *
 * This strategy executes orders in proportion to historical volume patterns,
 * aiming to achieve an average execution price close to the market VWAP.
 *
 * Parameters:
 * - targetQuantity: Total quantity to execute (default: 1000)
 * - durationMinutes: Duration over which to execute (default: 60)
 * - maxParticipationRate: Max % of volume to participate (default: 0.25)
 * - side: BUY or SELL (default: BUY)
 */
public class VwapStrategy extends AbstractTradingStrategy {

    private static final String NAME = "VWAP";
    private static final int DEFAULT_BUCKETS = 10;

    // Execution parameters
    private long targetQuantity;
    private long durationNanos;
    private double maxParticipationRate;
    private OrderSide side;

    // Execution state
    private long startTime;
    private long executedQuantity;
    private int currentBucket;
    private long[] scheduledQuantities;

    public VwapStrategy(Set<Symbol> symbols, StrategyParameters parameters) {
        this(symbols, parameters, null);
    }

    public VwapStrategy(Set<Symbol> symbols, StrategyParameters parameters, String customName) {
        super(symbols, parameters, customName);
        loadParameters();
    }

    private void loadParameters() {
        this.targetQuantity = parameters.getLong("targetQuantity", 1000);
        long durationMinutes = parameters.getLong("durationMinutes", 60);
        this.durationNanos = durationMinutes * 60 * 1_000_000_000L;
        this.maxParticipationRate = parameters.getDouble("maxParticipationRate", 0.25);
        String sideStr = parameters.getString("side", "BUY");
        this.side = "SELL".equalsIgnoreCase(sideStr) ? OrderSide.SELL : OrderSide.BUY;
        this.executedQuantity = 0;
        this.currentBucket = 0;

        // Distribute quantity across buckets (uniform for simplicity)
        scheduledQuantities = new long[DEFAULT_BUCKETS];
        long perBucket = targetQuantity / DEFAULT_BUCKETS;
        long remainder = targetQuantity - (perBucket * DEFAULT_BUCKETS);
        for (int i = 0; i < DEFAULT_BUCKETS; i++) {
            scheduledQuantities[i] = perBucket + (i < remainder ? 1 : 0);
        }
    }

    @Override
    protected void onStart() {
        this.startTime = context.getCurrentTimeNanos();
        context.logInfo(String.format("VWAP started: %s %d shares over %d minutes",
                side, targetQuantity, durationNanos / 60_000_000_000L));
    }

    @Override
    protected void onParametersUpdated() {
        loadParameters();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected double calculateSignal(Symbol symbol, Quote quote) {
        if (executedQuantity >= targetQuantity) {
            return 0.0; // Complete
        }

        // Calculate current bucket based on elapsed time
        long elapsed = context.getCurrentTimeNanos() - startTime;
        int bucket = (int) ((elapsed * DEFAULT_BUCKETS) / durationNanos);
        bucket = Math.min(bucket, DEFAULT_BUCKETS - 1);

        if (bucket > currentBucket) {
            currentBucket = bucket;
        }

        // Calculate expected vs actual execution
        long expectedByNow = 0;
        for (int i = 0; i <= currentBucket; i++) {
            expectedByNow += scheduledQuantities[i];
        }

        long remaining = targetQuantity - executedQuantity;
        if (remaining <= 0) {
            return 0.0;
        }

        // Signal strength based on how behind schedule we are
        double progress = (double) executedQuantity / targetQuantity;
        double expectedProgress = (double) expectedByNow / targetQuantity;
        double behindRatio = expectedProgress - progress;

        // Return signal: positive for BUY, negative for SELL
        double signal = Math.min(1.0, 0.5 + behindRatio);
        return side == OrderSide.BUY ? signal : -signal;
    }

    @Override
    protected long calculateTargetPosition(Symbol symbol, double signal) {
        if (Math.abs(signal) < 0.01) {
            return getCurrentPosition(symbol);
        }

        long remaining = targetQuantity - executedQuantity;
        if (remaining <= 0) {
            return 0;
        }

        // Calculate order size with participation rate limit
        long bucketTarget = currentBucket < DEFAULT_BUCKETS ? scheduledQuantities[currentBucket] : remaining;
        long orderSize = Math.min(bucketTarget, remaining);

        return side == OrderSide.BUY ? orderSize : -orderSize;
    }

    @Override
    public double getProgress() {
        return targetQuantity > 0 ? (executedQuantity * 100.0) / targetQuantity : 0;
    }

    @Override
    public void onFill(com.hft.core.model.Trade fill) {
        super.onFill(fill);
        executedQuantity += fill.getQuantity();

        if (executedQuantity >= targetQuantity) {
            context.logInfo(String.format("VWAP complete: executed %d/%d shares", executedQuantity, targetQuantity));
        }
    }
}
