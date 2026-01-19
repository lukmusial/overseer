package com.hft.risk.rules;

import com.hft.core.model.Order;
import com.hft.risk.RiskCheckResult;
import com.hft.risk.RiskContext;
import com.hft.risk.RiskRule;

/**
 * Checks that order size does not exceed maximum.
 */
public class MaxOrderSizeRule implements RiskRule {

    @Override
    public String getName() {
        return "MaxOrderSize";
    }

    @Override
    public int getPriority() {
        return 10; // Check early
    }

    @Override
    public RiskCheckResult check(Order order, RiskContext context) {
        long maxSize = context.getLimits().maxOrderSize();
        if (order.getQuantity() > maxSize) {
            return RiskCheckResult.reject(getName(),
                    "Order size " + order.getQuantity() + " exceeds max " + maxSize);
        }
        return RiskCheckResult.approve();
    }
}
