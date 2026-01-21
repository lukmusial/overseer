package com.hft.algo.base;

import com.hft.core.model.OrderSide;
import com.hft.core.model.Quote;
import com.hft.core.model.Symbol;
import com.hft.core.model.Trade;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract base class for trading strategies.
 * Provides common functionality for signal-based trading strategies.
 */
public abstract class AbstractTradingStrategy implements TradingStrategy {

    protected final String id;
    protected final String customName;
    protected final Set<Symbol> symbols;
    protected StrategyParameters parameters;
    protected AlgorithmContext context;

    protected final AtomicReference<AlgorithmState> state = new AtomicReference<>(AlgorithmState.INITIALIZED);

    // Position tracking
    protected final Map<Symbol, Long> currentPositions = new ConcurrentHashMap<>();
    protected final Map<Symbol, Long> targetPositions = new ConcurrentHashMap<>();
    protected final Map<Symbol, Double> signals = new ConcurrentHashMap<>();

    // P&L tracking
    protected final Map<Symbol, Long> avgEntryPrices = new ConcurrentHashMap<>();
    protected final AtomicLong totalRealizedPnl = new AtomicLong(0);
    protected final AtomicLong maxDrawdown = new AtomicLong(0);
    protected volatile long peakPnl = 0;

    // Quote cache
    protected final Map<Symbol, Quote> latestQuotes = new ConcurrentHashMap<>();

    // Execution tracking
    protected final AtomicLong ordersSubmitted = new AtomicLong(0);
    protected volatile long startTimeNanos;

    protected AbstractTradingStrategy(Set<Symbol> symbols, StrategyParameters parameters) {
        this(symbols, parameters, null);
    }

    protected AbstractTradingStrategy(Set<Symbol> symbols, StrategyParameters parameters, String customName) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.customName = customName;
        this.symbols = new HashSet<>(symbols);
        this.parameters = parameters != null ? parameters : new StrategyParameters();

        // Initialize positions
        for (Symbol symbol : symbols) {
            currentPositions.put(symbol, 0L);
            targetPositions.put(symbol, 0L);
            signals.put(symbol, 0.0);
        }
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * Gets the display name (custom name if set, otherwise the type name).
     */
    public String getDisplayName() {
        return customName != null && !customName.isBlank() ? customName : getName();
    }

    @Override
    public AlgorithmState getState() {
        return state.get();
    }

    @Override
    public Set<Symbol> getSymbols() {
        return Collections.unmodifiableSet(symbols);
    }

    @Override
    public double getSignal(Symbol symbol) {
        return signals.getOrDefault(symbol, 0.0);
    }

    @Override
    public long getTargetPosition(Symbol symbol) {
        return targetPositions.getOrDefault(symbol, 0L);
    }

    @Override
    public long getCurrentPosition(Symbol symbol) {
        return currentPositions.getOrDefault(symbol, 0L);
    }

    @Override
    public long getRealizedPnl() {
        return totalRealizedPnl.get();
    }

    @Override
    public long getUnrealizedPnl() {
        long unrealized = 0;
        for (Symbol symbol : symbols) {
            long position = currentPositions.getOrDefault(symbol, 0L);
            if (position != 0) {
                Quote quote = latestQuotes.get(symbol);
                long avgEntry = avgEntryPrices.getOrDefault(symbol, 0L);
                if (quote != null && avgEntry > 0) {
                    long currentPrice = (quote.getBidPrice() + quote.getAskPrice()) / 2;
                    unrealized += position * (currentPrice - avgEntry);
                }
            }
        }
        return unrealized;
    }

    @Override
    public long getMaxDrawdown() {
        return maxDrawdown.get();
    }

    @Override
    public StrategyParameters getParameters() {
        return parameters;
    }

    @Override
    public void updateParameters(StrategyParameters parameters) {
        this.parameters = parameters;
        onParametersUpdated();
    }

    @Override
    public void initialize(AlgorithmContext context) {
        this.context = context;
        onInitialize();
    }

    @Override
    public void start() {
        if (state.compareAndSet(AlgorithmState.INITIALIZED, AlgorithmState.RUNNING) ||
            state.compareAndSet(AlgorithmState.PAUSED, AlgorithmState.RUNNING)) {
            startTimeNanos = context.getCurrentTimeNanos();
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
            context.logInfo(getName() + " cancelled: " + id);
            onCancel();
        }
    }

    @Override
    public void onQuote(Quote quote) {
        if (state.get() != AlgorithmState.RUNNING) {
            return;
        }

        Symbol symbol = quote.getSymbol();
        if (!symbols.contains(symbol)) {
            return;
        }

        // Cache quote
        latestQuotes.put(symbol, quote);

        // Update signal
        double signal = calculateSignal(symbol, quote);
        signals.put(symbol, signal);

        // Calculate target position based on signal
        long target = calculateTargetPosition(symbol, signal);
        targetPositions.put(symbol, target);

        // Execute if needed
        executeTowardsTarget(symbol);

        // Update drawdown tracking
        updateDrawdown();
    }

    @Override
    public void onFill(Trade fill) {
        Symbol symbol = fill.getSymbol();
        if (!symbols.contains(symbol)) {
            return;
        }

        long fillQty = fill.getQuantity();
        long fillPrice = fill.getPrice();
        if (fill.getSide() == OrderSide.SELL) {
            fillQty = -fillQty;
        }

        long previousPosition = currentPositions.getOrDefault(symbol, 0L);
        long newPosition = previousPosition + fillQty;

        // Calculate P&L if reducing position
        if ((previousPosition > 0 && fillQty < 0) || (previousPosition < 0 && fillQty > 0)) {
            long avgEntry = avgEntryPrices.getOrDefault(symbol, fillPrice);
            long closingQty = Math.min(Math.abs(previousPosition), Math.abs(fillQty));
            long pnl = closingQty * (fillPrice - avgEntry);
            if (previousPosition < 0) {
                pnl = -pnl; // Short position: profit when price drops
            }
            totalRealizedPnl.addAndGet(pnl);
        }

        // Update average entry price if adding to position
        if ((previousPosition >= 0 && fillQty > 0) || (previousPosition <= 0 && fillQty < 0)) {
            long prevAvg = avgEntryPrices.getOrDefault(symbol, 0L);
            long prevQty = Math.abs(previousPosition);
            long addQty = Math.abs(fillQty);
            if (prevQty + addQty > 0) {
                long newAvg = (prevAvg * prevQty + fillPrice * addQty) / (prevQty + addQty);
                avgEntryPrices.put(symbol, newAvg);
            }
        }

        // Clear entry price if flat
        if (newPosition == 0) {
            avgEntryPrices.remove(symbol);
        }

        currentPositions.put(symbol, newPosition);

        context.logInfo(String.format("%s fill: %s %d @ %d, position: %d -> %d",
                getName(), symbol, fillQty, fillPrice, previousPosition, newPosition));
    }

    @Override
    public void onTimer(long timestampNanos) {
        if (state.get() != AlgorithmState.RUNNING) {
            return;
        }
        onTimerTick(timestampNanos);
    }

    @Override
    public double getProgress() {
        // Strategies run indefinitely, return 0
        return 0;
    }

    @Override
    public AlgorithmStats getStats() {
        return AlgorithmStats.builder()
                .algorithmId(id)
                .algorithmName(getName())
                .state(state.get())
                .startTimeNanos(startTimeNanos)
                .totalOrders(ordersSubmitted.get())
                .build();
    }

    /**
     * Executes orders to move towards target position.
     */
    protected void executeTowardsTarget(Symbol symbol) {
        long current = currentPositions.getOrDefault(symbol, 0L);
        long target = targetPositions.getOrDefault(symbol, 0L);
        long delta = target - current;

        if (delta == 0) {
            return;
        }

        Quote quote = latestQuotes.get(symbol);
        if (quote == null) {
            return;
        }

        OrderSide side = delta > 0 ? OrderSide.BUY : OrderSide.SELL;
        long quantity = Math.abs(delta);
        long price = side == OrderSide.BUY ? quote.getAskPrice() : quote.getBidPrice();

        // Apply position sizing limits
        long maxOrderSize = parameters.getLong("maxOrderSize", 1000);
        quantity = Math.min(quantity, maxOrderSize);

        if (quantity > 0) {
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
    }

    /**
     * Updates drawdown tracking.
     */
    protected void updateDrawdown() {
        long totalPnl = getRealizedPnl() + getUnrealizedPnl();
        if (totalPnl > peakPnl) {
            peakPnl = totalPnl;
        }
        long drawdown = peakPnl - totalPnl;
        if (drawdown > maxDrawdown.get()) {
            maxDrawdown.set(drawdown);
        }
    }

    // Abstract methods for subclasses

    /**
     * Calculates signal for a symbol (-1.0 to 1.0).
     */
    protected abstract double calculateSignal(Symbol symbol, Quote quote);

    /**
     * Calculates target position based on signal.
     */
    protected abstract long calculateTargetPosition(Symbol symbol, double signal);

    // Template methods
    protected void onInitialize() {}
    protected void onStart() {}
    protected void onPause() {}
    protected void onResume() {}
    protected void onCancel() {}
    protected void onParametersUpdated() {}
    protected void onTimerTick(long timestampNanos) {}
}
