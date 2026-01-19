package com.hft.risk;

import com.hft.core.model.Order;

/**
 * Interface for individual risk rules.
 * Rules are evaluated in sequence and the first rejection stops processing.
 */
public interface RiskRule {

    /**
     * Gets the unique name of this rule.
     */
    String getName();

    /**
     * Gets the priority of this rule (lower = evaluated first).
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Checks if the given order passes this risk rule.
     *
     * @param order   the order to check
     * @param context the risk context with current state
     * @return check result (approved or rejected with reason)
     */
    RiskCheckResult check(Order order, RiskContext context);

    /**
     * Whether this rule is enabled.
     */
    default boolean isEnabled() {
        return true;
    }
}
