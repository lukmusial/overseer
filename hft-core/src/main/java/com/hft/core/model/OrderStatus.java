package com.hft.core.model;

/**
 * Order lifecycle status.
 */
public enum OrderStatus {
    /** Order created but not yet sent to exchange */
    PENDING,

    /** Order sent to exchange, awaiting acknowledgment */
    SUBMITTED,

    /** Order acknowledged by exchange */
    ACCEPTED,

    /** Order partially filled */
    PARTIALLY_FILLED,

    /** Order fully filled */
    FILLED,

    /** Order cancelled by user or system */
    CANCELLED,

    /** Order rejected by exchange */
    REJECTED,

    /** Order expired (e.g., day order at market close) */
    EXPIRED;

    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED || this == EXPIRED;
    }

    public boolean isActive() {
        return this == SUBMITTED || this == ACCEPTED || this == PARTIALLY_FILLED;
    }
}
