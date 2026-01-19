package com.hft.risk.rules;

import com.hft.core.model.Order;
import com.hft.core.model.OrderSide;
import com.hft.core.model.Position;
import com.hft.risk.RiskCheckResult;
import com.hft.risk.RiskContext;
import com.hft.risk.RiskRule;

/**
 * Checks that resulting position size does not exceed maximum.
 */
public class MaxPositionSizeRule implements RiskRule {

    @Override
    public String getName() {
        return "MaxPositionSize";
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public RiskCheckResult check(Order order, RiskContext context) {
        Position position = context.getPosition(order.getSymbol());
        long currentQty = position != null ? position.getQuantity() : 0;

        long projectedQty;
        if (order.getSide() == OrderSide.BUY) {
            projectedQty = currentQty + order.getQuantity();
        } else {
            projectedQty = currentQty - order.getQuantity();
        }

        long maxSize = context.getLimits().maxPositionSize();
        if (Math.abs(projectedQty) > maxSize) {
            return RiskCheckResult.reject(getName(),
                    "Projected position " + projectedQty + " exceeds max " + maxSize);
        }
        return RiskCheckResult.approve();
    }
}
