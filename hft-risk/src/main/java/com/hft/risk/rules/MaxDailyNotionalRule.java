package com.hft.risk.rules;

import com.hft.core.model.Order;
import com.hft.risk.RiskCheckResult;
import com.hft.risk.RiskContext;
import com.hft.risk.RiskRule;

/**
 * Checks that daily notional traded does not exceed maximum.
 */
public class MaxDailyNotionalRule implements RiskRule {

    @Override
    public String getName() {
        return "MaxDailyNotional";
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public RiskCheckResult check(Order order, RiskContext context) {
        long orderNotional = order.getQuantity() * order.getPrice();
        long currentNotional = context.getNotionalTradedToday();
        long maxNotional = context.getLimits().maxDailyNotional();

        if (currentNotional + orderNotional > maxNotional) {
            return RiskCheckResult.reject(getName(),
                    "Daily notional would exceed limit: " +
                            (currentNotional + orderNotional) + " > " + maxNotional);
        }
        return RiskCheckResult.approve();
    }
}
