package com.hft.algo.base;

/**
 * Statistics for algorithm execution.
 */
public record AlgorithmStats(
        String algorithmId,
        String algorithmName,
        AlgorithmState state,
        long startTimeNanos,
        long endTimeNanos,
        long targetQuantity,
        long filledQuantity,
        long totalOrders,
        long filledOrders,
        long cancelledOrders,
        long rejectedOrders,
        long averageFillPrice,
        long benchmarkPrice,
        long slippageBps,
        double participationRate
) {
    /**
     * Gets the fill rate as a percentage.
     */
    public double fillRate() {
        return targetQuantity > 0 ? (double) filledQuantity / targetQuantity * 100.0 : 0.0;
    }

    /**
     * Gets execution duration in milliseconds.
     */
    public long durationMs() {
        if (endTimeNanos <= 0 || startTimeNanos <= 0) {
            return 0;
        }
        return (endTimeNanos - startTimeNanos) / 1_000_000;
    }

    /**
     * Creates a builder for AlgorithmStats.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String algorithmId;
        private String algorithmName;
        private AlgorithmState state = AlgorithmState.INITIALIZED;
        private long startTimeNanos;
        private long endTimeNanos;
        private long targetQuantity;
        private long filledQuantity;
        private long totalOrders;
        private long filledOrders;
        private long cancelledOrders;
        private long rejectedOrders;
        private long averageFillPrice;
        private long benchmarkPrice;
        private long slippageBps;
        private double participationRate;

        public Builder algorithmId(String algorithmId) {
            this.algorithmId = algorithmId;
            return this;
        }

        public Builder algorithmName(String algorithmName) {
            this.algorithmName = algorithmName;
            return this;
        }

        public Builder state(AlgorithmState state) {
            this.state = state;
            return this;
        }

        public Builder startTimeNanos(long startTimeNanos) {
            this.startTimeNanos = startTimeNanos;
            return this;
        }

        public Builder endTimeNanos(long endTimeNanos) {
            this.endTimeNanos = endTimeNanos;
            return this;
        }

        public Builder targetQuantity(long targetQuantity) {
            this.targetQuantity = targetQuantity;
            return this;
        }

        public Builder filledQuantity(long filledQuantity) {
            this.filledQuantity = filledQuantity;
            return this;
        }

        public Builder totalOrders(long totalOrders) {
            this.totalOrders = totalOrders;
            return this;
        }

        public Builder filledOrders(long filledOrders) {
            this.filledOrders = filledOrders;
            return this;
        }

        public Builder cancelledOrders(long cancelledOrders) {
            this.cancelledOrders = cancelledOrders;
            return this;
        }

        public Builder rejectedOrders(long rejectedOrders) {
            this.rejectedOrders = rejectedOrders;
            return this;
        }

        public Builder averageFillPrice(long averageFillPrice) {
            this.averageFillPrice = averageFillPrice;
            return this;
        }

        public Builder benchmarkPrice(long benchmarkPrice) {
            this.benchmarkPrice = benchmarkPrice;
            return this;
        }

        public Builder slippageBps(long slippageBps) {
            this.slippageBps = slippageBps;
            return this;
        }

        public Builder participationRate(double participationRate) {
            this.participationRate = participationRate;
            return this;
        }

        public AlgorithmStats build() {
            return new AlgorithmStats(
                    algorithmId, algorithmName, state,
                    startTimeNanos, endTimeNanos,
                    targetQuantity, filledQuantity,
                    totalOrders, filledOrders, cancelledOrders, rejectedOrders,
                    averageFillPrice, benchmarkPrice, slippageBps,
                    participationRate
            );
        }
    }
}
