package com.hft.risk.rules;

import com.hft.core.model.Order;
import com.hft.risk.RiskCheckResult;
import com.hft.risk.RiskContext;
import com.hft.risk.RiskRule;

/**
 * Checks that daily order count does not exceed maximum.
 */
public class MaxDailyOrdersRule implements RiskRule {

    @Override
    public String getName() {
        return "MaxDailyOrders";
    }

    @Override
    public int getPriority() {
        return 5; // Check very early
    }

    @Override
    public RiskCheckResult check(Order order, RiskContext context) {
        long ordersToday = context.getOrdersSubmittedToday();
        long maxOrders = context.getLimits().maxOrdersPerDay();

        if (ordersToday >= maxOrders) {
            return RiskCheckResult.reject(getName(),
                    "Daily order limit reached: " + ordersToday + " >= " + maxOrders);
        }
        return RiskCheckResult.approve();
    }
}
