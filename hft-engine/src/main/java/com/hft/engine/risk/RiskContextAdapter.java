package com.hft.engine.risk;

import com.hft.core.model.Position;
import com.hft.core.model.Symbol;
import com.hft.engine.service.PositionManager;
import com.hft.risk.RiskContext;
import com.hft.risk.RiskLimits;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapter that bridges PositionManager with the RiskContext interface.
 * Provides risk-related state to the RiskEngine.
 */
public class RiskContextAdapter implements RiskContext {

    private final PositionManager positionManager;
    private final RiskLimits limits;
    private final AtomicLong ordersSubmittedToday = new AtomicLong();
    private final AtomicLong notionalTradedToday = new AtomicLong();

    public RiskContextAdapter(PositionManager positionManager, RiskLimits limits) {
        this.positionManager = positionManager;
        this.limits = limits;
    }

    @Override
    public Position getPosition(Symbol symbol) {
        return positionManager.getPosition(symbol);
    }

    @Override
    public long getTotalPnl() {
        return positionManager.getTotalPnl();
    }

    @Override
    public long getUnrealizedPnl() {
        return positionManager.getTotalUnrealizedPnl();
    }

    @Override
    public long getRealizedPnl() {
        return positionManager.getTotalRealizedPnl();
    }

    @Override
    public long getNetExposure() {
        return positionManager.getNetExposure();
    }

    @Override
    public long getGrossExposure() {
        return positionManager.getGrossExposure().total();
    }

    @Override
    public long getOrdersSubmittedToday() {
        return ordersSubmittedToday.get();
    }

    @Override
    public long getNotionalTradedToday() {
        return notionalTradedToday.get();
    }

    @Override
    public RiskLimits getLimits() {
        return limits;
    }

    /**
     * Increments the order count for today.
     */
    public void incrementOrdersSubmitted() {
        ordersSubmittedToday.incrementAndGet();
    }

    /**
     * Adds to the notional traded today.
     */
    public void addNotionalTraded(long notional) {
        notionalTradedToday.addAndGet(notional);
    }

    /**
     * Resets daily counters.
     */
    public void resetDailyCounters() {
        ordersSubmittedToday.set(0);
        notionalTradedToday.set(0);
    }
}
