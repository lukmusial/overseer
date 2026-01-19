package com.hft.risk.rules;

import com.hft.core.model.Order;
import com.hft.core.model.OrderSide;
import com.hft.core.model.Position;
import com.hft.risk.RiskCheckResult;
import com.hft.risk.RiskContext;
import com.hft.risk.RiskRule;

/**
 * Checks that net exposure does not exceed maximum.
 * Net exposure = sum of (long positions - short positions) in dollar terms.
 */
public class MaxNetExposureRule implements RiskRule {

    @Override
    public String getName() {
        return "MaxNetExposure";
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public RiskCheckResult check(Order order, RiskContext context) {
        long currentExposure = context.getNetExposure();

        // Calculate the change this order would make
        long orderNotional = order.getQuantity() * order.getPrice();
        long projectedExposure;

        if (order.getSide() == OrderSide.BUY) {
            projectedExposure = currentExposure + orderNotional;
        } else {
            projectedExposure = currentExposure - orderNotional;
        }

        long maxExposure = context.getLimits().maxNetExposure();
        if (Math.abs(projectedExposure) > maxExposure) {
            return RiskCheckResult.reject(getName(),
                    "Net exposure would exceed limit: " +
                            Math.abs(projectedExposure) + " > " + maxExposure);
        }
        return RiskCheckResult.approve();
    }
}
