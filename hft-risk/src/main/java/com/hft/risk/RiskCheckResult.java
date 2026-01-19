package com.hft.risk;

/**
 * Result of a risk check operation.
 */
public record RiskCheckResult(
        boolean passed,
        String ruleName,
        String reason
) {
    public static RiskCheckResult approve() {
        return new RiskCheckResult(true, null, null);
    }

    public static RiskCheckResult reject(String ruleName, String reason) {
        return new RiskCheckResult(false, ruleName, reason);
    }

    public boolean approved() {
        return passed;
    }

    public boolean isRejected() {
        return !passed;
    }
}
