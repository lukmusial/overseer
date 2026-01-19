package com.hft.risk;

import com.hft.core.model.*;
import com.hft.risk.rules.StandardRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskEngineTest {

    private RiskEngine riskEngine;
    private TestRiskContext context;
    private Symbol symbol;

    @BeforeEach
    void setUp() {
        context = new TestRiskContext();
        riskEngine = new RiskEngine(RiskLimits.test(), context);
        StandardRules.addAllTo(riskEngine);
        symbol = new Symbol("AAPL", Exchange.ALPACA);
    }

    @Test
    void shouldApproveValidOrder() {
        // Create order within limits: 50 shares @ $10 = $500 notional (50,000 cents)
        // Test limits: maxOrderSize=100, maxOrderNotional=100,000 cents ($1k)
        Order order = createOrder(50, 1000);

        RiskCheckResult result = riskEngine.checkPreTrade(order);

        assertTrue(result.approved());
    }

    @Test
    void shouldRejectOrderExceedingMaxSize() {
        Order order = createOrder(200, 10000); // Max is 100 in test limits

        RiskCheckResult result = riskEngine.checkPreTrade(order);

        assertTrue(result.isRejected());
        assertEquals("MaxOrderSize", result.ruleName());
    }

    @Test
    void shouldRejectOrderExceedingMaxNotional() {
        Order order = createOrder(50, 100000); // 50 * 100000 = 5M, max is 1M in cents

        RiskCheckResult result = riskEngine.checkPreTrade(order);

        assertTrue(result.isRejected());
        assertEquals("MaxOrderNotional", result.ruleName());
    }

    @Test
    void shouldRejectWhenDailyLossLimitBreached() {
        context.setTotalPnl(-200_00); // -$200, limit is $100

        Order order = createOrder(10, 10000);
        RiskCheckResult result = riskEngine.checkPreTrade(order);

        assertTrue(result.isRejected());
        assertEquals("MaxDailyLoss", result.ruleName());
    }

    @Test
    void shouldRejectWhenDailyOrderLimitReached() {
        context.setOrdersSubmittedToday(100); // Limit is 100

        Order order = createOrder(10, 10000);
        RiskCheckResult result = riskEngine.checkPreTrade(order);

        assertTrue(result.isRejected());
        assertEquals("MaxDailyOrders", result.ruleName());
    }

    @Test
    void shouldRejectWhenRiskEngineDisabled() {
        riskEngine.disable("Test disabled");

        Order order = createOrder(10, 10000);
        RiskCheckResult result = riskEngine.checkPreTrade(order);

        assertTrue(result.isRejected());
        assertTrue(result.reason().contains("disabled"));
    }

    @Test
    void shouldRejectWhenCircuitBreakerTripped() {
        riskEngine.tripCircuitBreaker("Test trip");

        Order order = createOrder(10, 10000);
        RiskCheckResult result = riskEngine.checkPreTrade(order);

        assertTrue(result.isRejected());
        assertTrue(result.reason().contains("Circuit breaker"));
    }

    @Test
    void shouldTrackOrderStatistics() {
        // Use smaller prices to stay within notional limits
        // Test limits: maxOrderNotional = 100,000 cents ($1k)
        Order order1 = createOrder(10, 1000);  // 10 * 1000 = 10,000 cents
        Order order2 = createOrder(20, 1000);  // 20 * 1000 = 20,000 cents

        riskEngine.checkPreTrade(order1);
        riskEngine.checkPreTrade(order2);

        assertEquals(2, riskEngine.getOrdersApproved());
        assertEquals(0, riskEngine.getOrdersRejected());
    }

    @Test
    void shouldRecordFills() {
        riskEngine.recordFill(symbol, OrderSide.BUY, 100, 15000);
        riskEngine.recordFill(symbol, OrderSide.SELL, 50, 15500);

        long expectedNotional = (100 * 15000) + (50 * 15500);
        assertEquals(expectedNotional, riskEngine.getNotionalTradedToday());
    }

    @Test
    void shouldResetDailyCounters() {
        riskEngine.checkPreTrade(createOrder(10, 10000));
        riskEngine.recordFill(symbol, OrderSide.BUY, 100, 15000);

        riskEngine.resetDailyCounters();

        assertEquals(0, riskEngine.getOrdersSubmittedToday());
        assertEquals(0, riskEngine.getNotionalTradedToday());
    }

    @Test
    void shouldProvideSnapshot() {
        riskEngine.checkPreTrade(createOrder(10, 10000));
        riskEngine.checkPreTrade(createOrder(200, 10000)); // Will be rejected

        RiskEngine.RiskSnapshot snapshot = riskEngine.snapshot();

        assertTrue(snapshot.enabled());
        assertEquals(CircuitBreaker.State.CLOSED, snapshot.circuitBreakerState());
        assertEquals(1, snapshot.ordersApproved());
        assertEquals(1, snapshot.ordersRejected());
    }

    private Order createOrder(long quantity, long price) {
        return new Order()
                .symbol(symbol)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(quantity)
                .price(price);
    }

    /**
     * Test implementation of RiskContext.
     */
    private static class TestRiskContext implements RiskContext {
        private long totalPnl = 0;
        private long unrealizedPnl = 0;
        private long realizedPnl = 0;
        private long netExposure = 0;
        private long grossExposure = 0;
        private long ordersSubmittedToday = 0;
        private long notionalTradedToday = 0;

        @Override
        public Position getPosition(Symbol symbol) {
            return null;
        }

        @Override
        public long getTotalPnl() {
            return totalPnl;
        }

        public void setTotalPnl(long totalPnl) {
            this.totalPnl = totalPnl;
        }

        @Override
        public long getUnrealizedPnl() {
            return unrealizedPnl;
        }

        @Override
        public long getRealizedPnl() {
            return realizedPnl;
        }

        @Override
        public long getNetExposure() {
            return netExposure;
        }

        @Override
        public long getGrossExposure() {
            return grossExposure;
        }

        @Override
        public long getOrdersSubmittedToday() {
            return ordersSubmittedToday;
        }

        public void setOrdersSubmittedToday(long orders) {
            this.ordersSubmittedToday = orders;
        }

        @Override
        public long getNotionalTradedToday() {
            return notionalTradedToday;
        }

        @Override
        public RiskLimits getLimits() {
            return RiskLimits.test();
        }
    }
}
