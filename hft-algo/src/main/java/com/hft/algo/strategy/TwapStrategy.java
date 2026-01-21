package com.hft.algo.strategy;

import com.hft.algo.base.AbstractTradingStrategy;
import com.hft.algo.base.StrategyParameters;
import com.hft.core.model.OrderSide;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;

import java.util.*;

/**
 * TWAP (Time-Weighted Average Price) trading strategy.
 *
 * This strategy executes orders in equal slices over time,
 * aiming to achieve an average execution price close to the time-weighted market price.
 *
 * Parameters:
 * - targetQuantity: Total quantity to execute (default: 1000)
 * - durationMinutes: Duration over which to execute (default: 60)
 * - sliceIntervalSeconds: Interval between order slices (default: 60)
 * - maxParticipationRate: Max % of volume to participate (default: 0.25)
 * - side: BUY or SELL (default: BUY)
 */
public class TwapStrategy extends AbstractTradingStrategy {

    private static final String NAME = "TWAP";

    // Execution parameters
    private long targetQuantity;
    private long durationNanos;
    private long sliceIntervalNanos;
    private double maxParticipationRate;
    private OrderSide side;

    // Execution state
    private long startTime;
    private long executedQuantity;
    private int totalSlices;
    private int currentSlice;
    private long lastSliceTime;
    private long quantityPerSlice;

    public TwapStrategy(Set<Symbol> symbols, StrategyParameters parameters) {
        this(symbols, parameters, null);
    }

    public TwapStrategy(Set<Symbol> symbols, StrategyParameters parameters, String customName) {
        super(symbols, parameters, customName);
        loadParameters();
    }

    private void loadParameters() {
        this.targetQuantity = parameters.getLong("targetQuantity", 1000);
        long durationMinutes = parameters.getLong("durationMinutes", 60);
        this.durationNanos = durationMinutes * 60 * 1_000_000_000L;
        long sliceIntervalSeconds = parameters.getLong("sliceIntervalSeconds", 60);
        this.sliceIntervalNanos = sliceIntervalSeconds * 1_000_000_000L;
        this.maxParticipationRate = parameters.getDouble("maxParticipationRate", 0.25);
        String sideStr = parameters.getString("side", "BUY");
        this.side = "SELL".equalsIgnoreCase(sideStr) ? OrderSide.SELL : OrderSide.BUY;

        // Calculate slices
        this.totalSlices = Math.max(1, (int) (durationNanos / sliceIntervalNanos));
        this.quantityPerSlice = targetQuantity / totalSlices;
        this.executedQuantity = 0;
        this.currentSlice = 0;
    }

    @Override
    protected void onStart() {
        this.startTime = context.getCurrentTimeNanos();
        this.lastSliceTime = startTime;
        context.logInfo(String.format("TWAP started: %s %d shares in %d slices over %d minutes",
                side, targetQuantity, totalSlices, durationNanos / 60_000_000_000L));
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

        // Check if we're in a new slice
        long now = context.getCurrentTimeNanos();
        int newSlice = (int) ((now - startTime) / sliceIntervalNanos);
        newSlice = Math.min(newSlice, totalSlices - 1);

        if (newSlice > currentSlice) {
            currentSlice = newSlice;
            lastSliceTime = now;
        }

        // Calculate expected vs actual execution
        long expectedByNow = Math.min((currentSlice + 1) * quantityPerSlice, targetQuantity);
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

        // Calculate order size for this slice
        long sliceTarget = quantityPerSlice;

        // Include catch-up for missed quantities
        long expectedByNow = Math.min((currentSlice + 1) * quantityPerSlice, targetQuantity);
        long behind = expectedByNow - executedQuantity;
        if (behind > 0) {
            sliceTarget = Math.min(behind, remaining);
        }

        return side == OrderSide.BUY ? sliceTarget : -sliceTarget;
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
            context.logInfo(String.format("TWAP complete: executed %d/%d shares", executedQuantity, targetQuantity));
        }
    }
}
