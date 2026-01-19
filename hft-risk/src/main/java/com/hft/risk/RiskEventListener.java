package com.hft.risk;

import com.hft.core.model.Order;
import com.hft.core.model.OrderSide;
import com.hft.core.model.Symbol;

/**
 * Listener for risk events.
 */
public interface RiskEventListener {

    /**
     * Called when an order is rejected by risk check.
     */
    default void onOrderRejected(Order order, RiskCheckResult result) {}

    /**
     * Called when a fill is recorded.
     */
    default void onFillRecorded(Symbol symbol, OrderSide side, long quantity, long price) {}

    /**
     * Called when the risk engine is disabled.
     */
    default void onRiskEngineDisabled(String reason) {}

    /**
     * Called when a circuit breaker trips.
     */
    default void onCircuitBreakerTripped(String reason) {}
}
