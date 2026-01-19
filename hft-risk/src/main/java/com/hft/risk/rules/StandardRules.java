package com.hft.risk.rules;

import com.hft.risk.RiskEngine;
import com.hft.risk.RiskRule;

import java.util.List;

/**
 * Factory for standard risk rules.
 */
public final class StandardRules {

    private StandardRules() {}

    /**
     * Creates all standard risk rules.
     */
    public static List<RiskRule> all() {
        return List.of(
                new MaxDailyLossRule(),
                new MaxDailyOrdersRule(),
                new MaxOrderSizeRule(),
                new MaxOrderNotionalRule(),
                new MaxPositionSizeRule(),
                new MaxDailyNotionalRule(),
                new MaxNetExposureRule(),
                new MaxGrossExposureRule()
        );
    }

    /**
     * Creates basic risk rules (order and position limits only).
     */
    public static List<RiskRule> basic() {
        return List.of(
                new MaxOrderSizeRule(),
                new MaxOrderNotionalRule(),
                new MaxPositionSizeRule()
        );
    }

    /**
     * Adds all standard rules to a risk engine.
     */
    public static void addAllTo(RiskEngine engine) {
        for (RiskRule rule : all()) {
            engine.addRule(rule);
        }
    }

    /**
     * Adds basic rules to a risk engine.
     */
    public static void addBasicTo(RiskEngine engine) {
        for (RiskRule rule : basic()) {
            engine.addRule(rule);
        }
    }
}
