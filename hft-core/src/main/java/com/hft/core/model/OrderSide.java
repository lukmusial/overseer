package com.hft.core.model;

/**
 * Order side: BUY or SELL.
 */
public enum OrderSide {
    BUY(1),
    SELL(-1);

    private final int sign;

    OrderSide(int sign) {
        this.sign = sign;
    }

    /**
     * Returns 1 for BUY, -1 for SELL. Useful for position calculations.
     */
    public int getSign() {
        return sign;
    }

    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
