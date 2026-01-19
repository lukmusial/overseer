package com.hft.risk.rules;

import com.hft.core.model.Order;
import com.hft.risk.RiskCheckResult;
import com.hft.risk.RiskContext;
import com.hft.risk.RiskRule;

/**
 * Checks that daily loss has not exceeded maximum.
 * This is a hard stop - once the limit is breached, no more trading.
 */
public class MaxDailyLossRule implements RiskRule {

    @Override
    public String getName() {
        return "MaxDailyLoss";
    }

    @Override
    public int getPriority() {
        return 1; // Check first - this is critical
    }

    @Override
    public RiskCheckResult check(Order order, RiskContext context) {
        long totalPnl = context.getTotalPnl();
        long maxLoss = context.getLimits().maxDailyLoss();

        if (totalPnl < -maxLoss) {
            return RiskCheckResult.reject(getName(),
                    "Daily loss limit breached: P&L " + totalPnl + " < -" + maxLoss);
        }
        return RiskCheckResult.approve();
    }
}
