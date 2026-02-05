package com.hft.engine.service;

import com.hft.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages risk limits and pre-trade/post-trade risk checks.
 */
public class RiskManager {
    private static final Logger log = LoggerFactory.getLogger(RiskManager.class);

    private final PositionManager positionManager;
    private final RiskLimits limits;

    // Per-symbol tracking
    private final Map<Symbol, SymbolRiskState> symbolRiskStates;

    // Global counters
    private final AtomicLong ordersSubmittedToday;
    private final AtomicLong notionalTradedToday;
    private volatile boolean tradingEnabled = true;
    private volatile String disabledReason;

    public RiskManager(PositionManager positionManager, RiskLimits limits) {
        this.positionManager = positionManager;
        this.limits = limits;
        this.symbolRiskStates = new ConcurrentHashMap<>();
        this.ordersSubmittedToday = new AtomicLong();
        this.notionalTradedToday = new AtomicLong();
    }

    /**
     * Performs pre-trade risk check.
     * Returns rejection reason if rejected, null if approved.
     */
    public String checkPreTradeRisk(Order order) {
        if (!tradingEnabled) {
            return "Trading disabled: " + disabledReason;
        }

        // Check max orders per day
        if (ordersSubmittedToday.get() >= limits.maxOrdersPerDay()) {
            return "Max orders per day exceeded: " + limits.maxOrdersPerDay();
        }

        // Check position limits
        Position position = positionManager.getPosition(order.getSymbol());
        if (position != null) {
            long projectedQty = position.getQuantity();
            if (order.getSide() == OrderSide.BUY) {
                projectedQty += order.getQuantity();
            } else {
                projectedQty -= order.getQuantity();
            }

            if (Math.abs(projectedQty) > limits.maxPositionSize()) {
                return "Position limit exceeded: " + Math.abs(projectedQty) + " > " + limits.maxPositionSize();
            }
        }

        // Check order size
        if (order.getQuantity() > limits.maxOrderSize()) {
            return "Order size exceeded: " + order.getQuantity() + " > " + limits.maxOrderSize();
        }

        // Check notional value (convert price from internal representation to dollars)
        long orderNotional = order.getQuantity() * order.getPrice() / order.getPriceScale();
        if (orderNotional > limits.maxOrderNotional()) {
            return "Order notional exceeded: " + orderNotional + " > " + limits.maxOrderNotional();
        }

        // Check daily notional
        if (notionalTradedToday.get() + orderNotional > limits.maxDailyNotional()) {
            return "Daily notional limit exceeded";
        }

        // Check loss limits (compare in cents since positions may have different price scales)
        long totalPnlCents = positionManager.getTotalPnlCents();
        if (totalPnlCents < -limits.maxDailyLoss()) {
            return "Daily loss limit exceeded: " + (totalPnlCents / 100) + " dollars (limit: $" + (limits.maxDailyLoss() / 100) + ")";
        }

        // Increment counter
        ordersSubmittedToday.incrementAndGet();

        return null; // Approved
    }

    /**
     * Records a fill for risk tracking.
     */
    public void recordFill(Symbol symbol, OrderSide side, long quantity, long price, int priceScale) {
        long notional = quantity * price / priceScale;
        notionalTradedToday.addAndGet(notional);

        SymbolRiskState state = symbolRiskStates.computeIfAbsent(symbol, s -> new SymbolRiskState());
        state.recordFill(quantity, notional);
    }

    /**
     * Checks position-level risk limits.
     */
    public void checkPositionLimits(Symbol symbol) {
        Position position = positionManager.getPosition(symbol);
        if (position == null) {
            return;
        }

        // Check drawdown
        if (position.getMaxDrawdown() < -limits.maxDrawdownPerPosition()) {
            log.warn("Position drawdown limit breached for {}: {}",
                    symbol, position.getMaxDrawdown());
        }

        // Check unrealized loss
        if (position.getUnrealizedPnl() < -limits.maxUnrealizedLossPerPosition()) {
            log.warn("Position unrealized loss limit breached for {}: {}",
                    symbol, position.getUnrealizedPnl());
        }
    }

    /**
     * Checks global P&L limits.
     */
    public boolean checkGlobalLimits() {
        long totalPnl = positionManager.getTotalPnl();

        if (totalPnl < -limits.maxDailyLoss()) {
            disableTradingWithReason("Daily loss limit breached: " + totalPnl);
            return false;
        }

        long exposure = positionManager.getNetExposure();
        if (exposure > limits.maxNetExposure()) {
            log.warn("Net exposure limit breached: {} > {}", exposure, limits.maxNetExposure());
        }

        return true;
    }

    /**
     * Disables trading with a reason.
     */
    public void disableTradingWithReason(String reason) {
        this.tradingEnabled = false;
        this.disabledReason = reason;
        log.error("Trading disabled: {}", reason);
    }

    /**
     * Re-enables trading.
     */
    public void enableTrading() {
        this.tradingEnabled = true;
        this.disabledReason = null;
        log.info("Trading enabled");
    }

    /**
     * Resets daily counters (call at start of day).
     */
    public void resetDailyCounters() {
        ordersSubmittedToday.set(0);
        notionalTradedToday.set(0);
        symbolRiskStates.clear();
        log.info("Daily risk counters reset");
    }

    public boolean isTradingEnabled() {
        return tradingEnabled;
    }

    public String getDisabledReason() {
        return disabledReason;
    }

    public long getOrdersSubmittedToday() {
        return ordersSubmittedToday.get();
    }

    public long getNotionalTradedToday() {
        return notionalTradedToday.get();
    }

    public RiskLimits getLimits() {
        return limits;
    }

    /**
     * Risk limits configuration.
     */
    public record RiskLimits(
            long maxOrderSize,           // Max shares per order
            long maxOrderNotional,       // Max notional per order
            long maxPositionSize,        // Max shares per symbol
            long maxOrdersPerDay,        // Max orders per day
            long maxDailyNotional,       // Max notional traded per day
            long maxDailyLoss,           // Max loss before trading stops
            long maxDrawdownPerPosition, // Max drawdown per position
            long maxUnrealizedLossPerPosition, // Max unrealized loss per position
            long maxNetExposure          // Max total net exposure
    ) {
        public static RiskLimits defaults() {
            return new RiskLimits(
                    10_000,         // 10k shares per order
                    1_000_000,      // $1M per order
                    100_000,        // 100k shares per symbol
                    10_000,         // 10k orders per day
                    10_000_000,     // $10M per day
                    100_000,        // $100k daily loss limit
                    50_000,         // $50k drawdown per position
                    25_000,         // $25k unrealized loss per position
                    5_000_000       // $5M net exposure
            );
        }

        public static RiskLimits conservative() {
            return new RiskLimits(
                    1_000,          // 1k shares per order
                    100_000,        // $100k per order
                    10_000,         // 10k shares per symbol
                    1_000,          // 1k orders per day
                    1_000_000,      // $1M per day
                    10_000,         // $10k daily loss limit
                    5_000,          // $5k drawdown per position
                    2_500,          // $2.5k unrealized loss per position
                    500_000         // $500k net exposure
            );
        }
    }

    private static class SymbolRiskState {
        private final AtomicLong quantityTraded = new AtomicLong();
        private final AtomicLong notionalTraded = new AtomicLong();

        void recordFill(long quantity, long notional) {
            quantityTraded.addAndGet(quantity);
            notionalTraded.addAndGet(notional);
        }
    }
}
