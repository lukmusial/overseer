package com.hft.risk;

/**
 * Risk limits configuration.
 * All monetary values are in cents for precision.
 */
public record RiskLimits(
        long maxOrderSize,                    // Max shares per order
        long maxOrderNotional,                // Max notional per order (cents)
        long maxPositionSize,                 // Max shares per symbol
        long maxOrdersPerDay,                 // Max orders per day
        long maxDailyNotional,                // Max notional traded per day (cents)
        long maxDailyLoss,                    // Max loss before trading stops (cents)
        long maxDrawdownPerPosition,          // Max drawdown per position (cents)
        long maxUnrealizedLossPerPosition,    // Max unrealized loss per position (cents)
        long maxNetExposure,                  // Max total net exposure (cents)
        long maxGrossExposure,                // Max total gross exposure (cents)
        int circuitBreakerThreshold,          // Number of rejects before circuit trips
        long circuitBreakerCooldownMs         // Cooldown period in milliseconds
) {
    /**
     * Default limits suitable for paper trading.
     */
    public static RiskLimits defaults() {
        return new RiskLimits(
                10_000,             // 10k shares per order
                100_000_00,         // $1M per order (in cents)
                100_000,            // 100k shares per symbol
                10_000,             // 10k orders per day
                1_000_000_000,      // $10M per day (in cents)
                10_000_00,          // $100k daily loss limit (in cents)
                5_000_00,           // $50k drawdown per position (in cents)
                2_500_00,           // $25k unrealized loss per position (in cents)
                500_000_000,        // $5M net exposure (in cents)
                1_000_000_000,      // $10M gross exposure (in cents)
                10,                 // 10 rejects trigger circuit breaker
                60_000              // 1 minute cooldown
        );
    }

    /**
     * Conservative limits for live trading with smaller capital.
     */
    public static RiskLimits conservative() {
        return new RiskLimits(
                1_000,              // 1k shares per order
                10_000_00,          // $100k per order (in cents)
                10_000,             // 10k shares per symbol
                1_000,              // 1k orders per day
                100_000_000,        // $1M per day (in cents)
                1_000_00,           // $10k daily loss limit (in cents)
                500_00,             // $5k drawdown per position (in cents)
                250_00,             // $2.5k unrealized loss per position (in cents)
                50_000_000,         // $500k net exposure (in cents)
                100_000_000,        // $1M gross exposure (in cents)
                5,                  // 5 rejects trigger circuit breaker
                120_000             // 2 minute cooldown
        );
    }

    /**
     * Very tight limits for testing.
     */
    public static RiskLimits test() {
        return new RiskLimits(
                100,                // 100 shares per order
                1_000_00,           // $10k per order (in cents)
                500,                // 500 shares per symbol
                100,                // 100 orders per day
                10_000_00,          // $100k per day (in cents)
                100_00,             // $1k daily loss limit (in cents)
                50_00,              // $500 drawdown per position (in cents)
                25_00,              // $250 unrealized loss per position (in cents)
                5_000_00,           // $50k net exposure (in cents)
                10_000_00,          // $100k gross exposure (in cents)
                3,                  // 3 rejects trigger circuit breaker
                30_000              // 30 second cooldown
        );
    }

    /**
     * Builder for custom limits.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long maxOrderSize = 10_000;
        private long maxOrderNotional = 100_000_00;
        private long maxPositionSize = 100_000;
        private long maxOrdersPerDay = 10_000;
        private long maxDailyNotional = 1_000_000_000;
        private long maxDailyLoss = 10_000_00;
        private long maxDrawdownPerPosition = 5_000_00;
        private long maxUnrealizedLossPerPosition = 2_500_00;
        private long maxNetExposure = 500_000_000;
        private long maxGrossExposure = 1_000_000_000;
        private int circuitBreakerThreshold = 10;
        private long circuitBreakerCooldownMs = 60_000;

        public Builder maxOrderSize(long value) {
            this.maxOrderSize = value;
            return this;
        }

        public Builder maxOrderNotional(long value) {
            this.maxOrderNotional = value;
            return this;
        }

        public Builder maxPositionSize(long value) {
            this.maxPositionSize = value;
            return this;
        }

        public Builder maxOrdersPerDay(long value) {
            this.maxOrdersPerDay = value;
            return this;
        }

        public Builder maxDailyNotional(long value) {
            this.maxDailyNotional = value;
            return this;
        }

        public Builder maxDailyLoss(long value) {
            this.maxDailyLoss = value;
            return this;
        }

        public Builder maxDrawdownPerPosition(long value) {
            this.maxDrawdownPerPosition = value;
            return this;
        }

        public Builder maxUnrealizedLossPerPosition(long value) {
            this.maxUnrealizedLossPerPosition = value;
            return this;
        }

        public Builder maxNetExposure(long value) {
            this.maxNetExposure = value;
            return this;
        }

        public Builder maxGrossExposure(long value) {
            this.maxGrossExposure = value;
            return this;
        }

        public Builder circuitBreakerThreshold(int value) {
            this.circuitBreakerThreshold = value;
            return this;
        }

        public Builder circuitBreakerCooldownMs(long value) {
            this.circuitBreakerCooldownMs = value;
            return this;
        }

        public RiskLimits build() {
            return new RiskLimits(
                    maxOrderSize, maxOrderNotional, maxPositionSize,
                    maxOrdersPerDay, maxDailyNotional, maxDailyLoss,
                    maxDrawdownPerPosition, maxUnrealizedLossPerPosition,
                    maxNetExposure, maxGrossExposure,
                    circuitBreakerThreshold, circuitBreakerCooldownMs
            );
        }
    }
}
