package com.hft.risk;

import com.hft.core.model.Order;
import com.hft.core.model.OrderSide;
import com.hft.core.model.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main risk engine that coordinates risk rules and circuit breaker.
 */
public class RiskEngine {
    private static final Logger log = LoggerFactory.getLogger(RiskEngine.class);

    private final List<RiskRule> rules;
    private final CircuitBreaker circuitBreaker;
    private final RiskLimits limits;
    private final RiskContext context;

    // Counters
    private final AtomicLong ordersSubmittedToday = new AtomicLong();
    private final AtomicLong notionalTradedToday = new AtomicLong();
    private final AtomicLong ordersApproved = new AtomicLong();
    private final AtomicLong ordersRejected = new AtomicLong();

    // Per-symbol tracking
    private final Map<Symbol, SymbolRiskState> symbolStates = new ConcurrentHashMap<>();

    // Listeners
    private final List<RiskEventListener> listeners = new ArrayList<>();

    private volatile boolean enabled = true;
    private volatile String disabledReason;

    public RiskEngine(RiskLimits limits, RiskContext context) {
        this.limits = limits;
        this.context = context;
        this.circuitBreaker = new CircuitBreaker(
                limits.circuitBreakerThreshold(),
                limits.circuitBreakerCooldownMs()
        );
        this.rules = new ArrayList<>();
    }

    /**
     * Adds a risk rule.
     */
    public void addRule(RiskRule rule) {
        rules.add(rule);
        rules.sort(Comparator.comparingInt(RiskRule::getPriority));
        log.info("Added risk rule: {} (priority {})", rule.getName(), rule.getPriority());
    }

    /**
     * Removes a risk rule by name.
     */
    public void removeRule(String ruleName) {
        rules.removeIf(r -> r.getName().equals(ruleName));
    }

    /**
     * Adds a risk event listener.
     */
    public void addListener(RiskEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Performs pre-trade risk check.
     *
     * @param order the order to check
     * @return check result
     */
    public RiskCheckResult checkPreTrade(Order order) {
        // Check if engine is enabled
        if (!enabled) {
            return RiskCheckResult.reject("RiskEngine", "Risk engine disabled: " + disabledReason);
        }

        // Check circuit breaker
        String circuitCheck = circuitBreaker.checkAllowed();
        if (circuitCheck != null) {
            return RiskCheckResult.reject("CircuitBreaker", circuitCheck);
        }

        // Run all rules
        for (RiskRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }

            try {
                RiskCheckResult result = rule.check(order, context);
                if (result.isRejected()) {
                    ordersRejected.incrementAndGet();
                    circuitBreaker.recordFailure(result.reason());
                    notifyRejection(order, result);
                    return result;
                }
            } catch (Exception e) {
                log.error("Risk rule {} threw exception", rule.getName(), e);
                RiskCheckResult result = RiskCheckResult.reject(rule.getName(), "Rule error: " + e.getMessage());
                circuitBreaker.recordFailure(result.reason());
                return result;
            }
        }

        // All rules passed
        ordersApproved.incrementAndGet();
        ordersSubmittedToday.incrementAndGet();
        circuitBreaker.recordSuccess();

        return RiskCheckResult.approve();
    }

    /**
     * Records a fill for risk tracking.
     */
    public void recordFill(Symbol symbol, OrderSide side, long quantity, long price) {
        long notional = quantity * price;
        notionalTradedToday.addAndGet(notional);

        SymbolRiskState state = symbolStates.computeIfAbsent(symbol, s -> new SymbolRiskState());
        state.recordFill(quantity, notional, side);

        notifyFill(symbol, side, quantity, price);
    }

    /**
     * Disables the risk engine.
     */
    public void disable(String reason) {
        this.enabled = false;
        this.disabledReason = reason;
        log.error("Risk engine disabled: {}", reason);
        notifyDisabled(reason);
    }

    /**
     * Enables the risk engine.
     */
    public void enable() {
        this.enabled = true;
        this.disabledReason = null;
        log.info("Risk engine enabled");
    }

    /**
     * Trips the circuit breaker manually.
     */
    public void tripCircuitBreaker(String reason) {
        circuitBreaker.trip(reason);
    }

    /**
     * Resets the circuit breaker.
     */
    public void resetCircuitBreaker() {
        circuitBreaker.reset();
    }

    /**
     * Resets daily counters.
     */
    public void resetDailyCounters() {
        ordersSubmittedToday.set(0);
        notionalTradedToday.set(0);
        ordersApproved.set(0);
        ordersRejected.set(0);
        symbolStates.clear();
        log.info("Daily risk counters reset");
    }

    // Getters

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisabledReason() {
        return disabledReason;
    }

    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }

    /**
     * Checks circuit breaker state, potentially triggering HALF_OPEN transition after cooldown.
     * Returns the state after the check.
     */
    public CircuitBreaker.State checkCircuitBreakerState() {
        circuitBreaker.checkAllowed(); // This may transition from OPEN to HALF_OPEN
        return circuitBreaker.getState();
    }

    public long getOrdersSubmittedToday() {
        return ordersSubmittedToday.get();
    }

    public long getNotionalTradedToday() {
        return notionalTradedToday.get();
    }

    public long getOrdersApproved() {
        return ordersApproved.get();
    }

    public long getOrdersRejected() {
        return ordersRejected.get();
    }

    public RiskLimits getLimits() {
        return limits;
    }

    public List<RiskRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public RiskSnapshot snapshot() {
        return new RiskSnapshot(
                enabled,
                circuitBreaker.getState(),
                ordersSubmittedToday.get(),
                notionalTradedToday.get(),
                ordersApproved.get(),
                ordersRejected.get(),
                context.getTotalPnl(),
                context.getNetExposure(),
                context.getGrossExposure()
        );
    }

    // Event notifications

    private void notifyRejection(Order order, RiskCheckResult result) {
        for (RiskEventListener listener : listeners) {
            try {
                listener.onOrderRejected(order, result);
            } catch (Exception e) {
                log.warn("Listener error on rejection", e);
            }
        }
    }

    private void notifyFill(Symbol symbol, OrderSide side, long quantity, long price) {
        for (RiskEventListener listener : listeners) {
            try {
                listener.onFillRecorded(symbol, side, quantity, price);
            } catch (Exception e) {
                log.warn("Listener error on fill", e);
            }
        }
    }

    private void notifyDisabled(String reason) {
        for (RiskEventListener listener : listeners) {
            try {
                listener.onRiskEngineDisabled(reason);
            } catch (Exception e) {
                log.warn("Listener error on disable", e);
            }
        }
    }

    /**
     * Risk engine snapshot.
     */
    public record RiskSnapshot(
            boolean enabled,
            CircuitBreaker.State circuitBreakerState,
            long ordersSubmittedToday,
            long notionalTradedToday,
            long ordersApproved,
            long ordersRejected,
            long totalPnl,
            long netExposure,
            long grossExposure
    ) {}

    /**
     * Per-symbol risk tracking.
     */
    private static class SymbolRiskState {
        private final AtomicLong quantityTraded = new AtomicLong();
        private final AtomicLong notionalTraded = new AtomicLong();
        private final AtomicLong buyQuantity = new AtomicLong();
        private final AtomicLong sellQuantity = new AtomicLong();

        void recordFill(long quantity, long notional, OrderSide side) {
            quantityTraded.addAndGet(quantity);
            notionalTraded.addAndGet(notional);
            if (side == OrderSide.BUY) {
                buyQuantity.addAndGet(quantity);
            } else {
                sellQuantity.addAndGet(quantity);
            }
        }
    }
}
