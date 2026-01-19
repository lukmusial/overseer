package com.hft.risk.rules;

import com.hft.core.model.Order;
import com.hft.risk.RiskCheckResult;
import com.hft.risk.RiskContext;
import com.hft.risk.RiskRule;

/**
 * Checks that order notional value does not exceed maximum.
 */
public class MaxOrderNotionalRule implements RiskRule {

    @Override
    public String getName() {
        return "MaxOrderNotional";
    }

    @Override
    public int getPriority() {
        return 11;
    }

    @Override
    public RiskCheckResult check(Order order, RiskContext context) {
        long notional = order.getQuantity() * order.getPrice();
        long maxNotional = context.getLimits().maxOrderNotional();

        if (notional > maxNotional) {
            return RiskCheckResult.reject(getName(),
                    "Order notional " + notional + " exceeds max " + maxNotional);
        }
        return RiskCheckResult.approve();
    }
}
