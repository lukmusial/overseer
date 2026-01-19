package com.hft.risk.rules;

import com.hft.core.model.Order;
import com.hft.risk.RiskCheckResult;
import com.hft.risk.RiskContext;
import com.hft.risk.RiskRule;

/**
 * Checks that gross exposure does not exceed maximum.
 * Gross exposure = sum of absolute values of all positions in dollar terms.
 */
public class MaxGrossExposureRule implements RiskRule {

    @Override
    public String getName() {
        return "MaxGrossExposure";
    }

    @Override
    public int getPriority() {
        return 41;
    }

    @Override
    public RiskCheckResult check(Order order, RiskContext context) {
        long currentGrossExposure = context.getGrossExposure();
        long orderNotional = order.getQuantity() * order.getPrice();

        // Worst case: order increases exposure (conservative check)
        long projectedExposure = currentGrossExposure + orderNotional;

        long maxExposure = context.getLimits().maxGrossExposure();
        if (projectedExposure > maxExposure) {
            return RiskCheckResult.reject(getName(),
                    "Gross exposure would exceed limit: " +
                            projectedExposure + " > " + maxExposure);
        }
        return RiskCheckResult.approve();
    }
}
