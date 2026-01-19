package com.hft.engine.service;

import com.hft.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskManagerTest {

    private PositionManager positionManager;
    private RiskManager riskManager;
    private Symbol testSymbol;
    private RiskManager.RiskLimits testLimits;

    @BeforeEach
    void setUp() {
        positionManager = new PositionManager();
        testLimits = new RiskManager.RiskLimits(
                1000,      // maxOrderSize: 1000 shares
                100000,    // maxOrderNotional: $1000
                5000,      // maxPositionSize: 5000 shares
                100,       // maxOrdersPerDay: 100
                1000000,   // maxDailyNotional: $10,000
                50000,     // maxDailyLoss: $500
                10000,     // maxDrawdownPerPosition: $100
                5000,      // maxUnrealizedLossPerPosition: $50
                500000     // maxNetExposure: $5000
        );
        riskManager = new RiskManager(positionManager, testLimits);
        testSymbol = new Symbol("AAPL", Exchange.ALPACA);
    }

    @Test
    void checkPreTradeRisk_WhenTradingDisabled_ShouldReject() {
        riskManager.disableTradingWithReason("Test disabled");

        Order order = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        String rejection = riskManager.checkPreTradeRisk(order);

        assertNotNull(rejection);
        assertTrue(rejection.contains("Trading disabled"));
    }

    @Test
    void checkPreTradeRisk_WhenValidOrder_ShouldApprove() {
        Order order = createOrder(testSymbol, OrderSide.BUY, 100, 15000L);
        String rejection = riskManager.checkPreTradeRisk(order);

        assertNull(rejection);
    }

    @Test
    void checkPreTradeRisk_WhenOrderSizeExceeded_ShouldReject() {
        Order order = createOrder(testSymbol, OrderSide.BUY, 2000, 15000L); // Over 1000 limit
        String rejection = riskManager.checkPreTradeRisk(order);

        assertNotNull(rejection);
        assertTrue(rejection.contains("Order size exceeded"));
    }

    @Test
    void checkPreTradeRisk_WhenOrderNotionalExceeded_ShouldReject() {
        // 500 shares * $300 = $150,000 notional (over $1000 limit)
        Order order = createOrder(testSymbol, OrderSide.BUY, 500, 30000L);
        String rejection = riskManager.checkPreTradeRisk(order);

        assertNotNull(rejection);
        assertTrue(rejection.contains("Order notional exceeded"));
    }

    @Test
    void checkPreTradeRisk_WhenPositionLimitExceeded_ShouldReject() {
        // Create existing position of 4500 shares
        Trade trade = new Trade();
        trade.setSymbol(testSymbol);
        trade.setSide(OrderSide.BUY);
        trade.setQuantity(4500);
        trade.setPrice(15000L);
        positionManager.applyTrade(trade);

        // Try to buy 600 more (would be 5100, over 5000 limit)
        Order order = createOrder(testSymbol, OrderSide.BUY, 600, 15000L);
        String rejection = riskManager.checkPreTradeRisk(order);

        assertNotNull(rejection);
        assertTrue(rejection.contains("Position limit exceeded"));
    }

    @Test
    void checkPreTradeRisk_WhenSellReducesPosition_ShouldApprove() {
        // Create existing long position
        Trade trade = new Trade();
        trade.setSymbol(testSymbol);
        trade.setSide(OrderSide.BUY);
        trade.setQuantity(4500);
        trade.setPrice(15000L);
        positionManager.applyTrade(trade);

        // Selling reduces position, should be approved
        Order order = createOrder(testSymbol, OrderSide.SELL, 500, 15000L);
        String rejection = riskManager.checkPreTradeRisk(order);

        assertNull(rejection);
    }

    @Test
    void checkPreTradeRisk_WhenMaxOrdersExceeded_ShouldReject() {
        // Submit orders up to the limit
        for (int i = 0; i < 100; i++) {
            Order order = createOrder(testSymbol, OrderSide.BUY, 10, 15000L);
            String rejection = riskManager.checkPreTradeRisk(order);
            assertNull(rejection, "Order " + i + " should be approved");
        }

        // 101st order should be rejected
        Order order = createOrder(testSymbol, OrderSide.BUY, 10, 15000L);
        String rejection = riskManager.checkPreTradeRisk(order);

        assertNotNull(rejection);
        assertTrue(rejection.contains("Max orders per day exceeded"));
    }

    @Test
    void checkPreTradeRisk_WhenDailyLossExceeded_ShouldReject() {
        // Create position with loss
        Trade buyTrade = new Trade();
        buyTrade.setSymbol(testSymbol);
        buyTrade.setSide(OrderSide.BUY);
        buyTrade.setQuantity(1000);
        buyTrade.setPrice(20000L); // $200
        positionManager.applyTrade(buyTrade);

        // Sell at loss - $150 per share, $50 loss per share * 1000 = $50,000 loss
        Trade sellTrade = new Trade();
        sellTrade.setSymbol(testSymbol);
        sellTrade.setSide(OrderSide.SELL);
        sellTrade.setQuantity(1000);
        sellTrade.setPrice(15000L);
        positionManager.applyTrade(sellTrade);

        // Now total P&L is at the loss limit
        Order order = createOrder(testSymbol, OrderSide.BUY, 10, 15000L);
        String rejection = riskManager.checkPreTradeRisk(order);

        assertNotNull(rejection);
        assertTrue(rejection.contains("Daily loss limit exceeded"));
    }

    @Test
    void recordFill_ShouldUpdateNotionalTraded() {
        riskManager.recordFill(testSymbol, OrderSide.BUY, 100, 15000L);

        long notionalTraded = riskManager.getNotionalTradedToday();
        assertEquals(15000L, notionalTraded); // 100 * 15000 / 100 = 15000
    }

    @Test
    void checkGlobalLimits_WhenPnLOk_ShouldReturnTrue() {
        assertTrue(riskManager.checkGlobalLimits());
        assertTrue(riskManager.isTradingEnabled());
    }

    @Test
    void checkGlobalLimits_WhenLossLimitBreached_ShouldDisableTrading() {
        // Create large loss
        Trade buyTrade = new Trade();
        buyTrade.setSymbol(testSymbol);
        buyTrade.setSide(OrderSide.BUY);
        buyTrade.setQuantity(1000);
        buyTrade.setPrice(20000L);
        positionManager.applyTrade(buyTrade);

        Trade sellTrade = new Trade();
        sellTrade.setSymbol(testSymbol);
        sellTrade.setSide(OrderSide.SELL);
        sellTrade.setQuantity(1000);
        sellTrade.setPrice(14000L); // $60 loss per share
        positionManager.applyTrade(sellTrade);

        boolean result = riskManager.checkGlobalLimits();

        assertFalse(result);
        assertFalse(riskManager.isTradingEnabled());
        assertNotNull(riskManager.getDisabledReason());
    }

    @Test
    void disableTradingWithReason_ShouldSetReasonAndFlag() {
        riskManager.disableTradingWithReason("Manual halt");

        assertFalse(riskManager.isTradingEnabled());
        assertEquals("Manual halt", riskManager.getDisabledReason());
    }

    @Test
    void enableTrading_ShouldClearDisabledState() {
        riskManager.disableTradingWithReason("Test");
        assertFalse(riskManager.isTradingEnabled());

        riskManager.enableTrading();

        assertTrue(riskManager.isTradingEnabled());
        assertNull(riskManager.getDisabledReason());
    }

    @Test
    void resetDailyCounters_ShouldClearAllCounters() {
        // Accumulate some activity
        for (int i = 0; i < 10; i++) {
            riskManager.checkPreTradeRisk(createOrder(testSymbol, OrderSide.BUY, 10, 15000L));
        }
        riskManager.recordFill(testSymbol, OrderSide.BUY, 100, 15000L);

        assertTrue(riskManager.getOrdersSubmittedToday() > 0);
        assertTrue(riskManager.getNotionalTradedToday() > 0);

        riskManager.resetDailyCounters();

        assertEquals(0, riskManager.getOrdersSubmittedToday());
        assertEquals(0, riskManager.getNotionalTradedToday());
    }

    @Test
    void defaultLimits_ShouldHaveReasonableValues() {
        RiskManager.RiskLimits defaults = RiskManager.RiskLimits.defaults();

        assertEquals(10_000, defaults.maxOrderSize());
        assertEquals(1_000_000, defaults.maxOrderNotional());
        assertEquals(100_000, defaults.maxPositionSize());
        assertEquals(10_000, defaults.maxOrdersPerDay());
        assertEquals(10_000_000, defaults.maxDailyNotional());
        assertEquals(100_000, defaults.maxDailyLoss());
    }

    @Test
    void conservativeLimits_ShouldBeLowerThanDefaults() {
        RiskManager.RiskLimits defaults = RiskManager.RiskLimits.defaults();
        RiskManager.RiskLimits conservative = RiskManager.RiskLimits.conservative();

        assertTrue(conservative.maxOrderSize() < defaults.maxOrderSize());
        assertTrue(conservative.maxOrderNotional() < defaults.maxOrderNotional());
        assertTrue(conservative.maxPositionSize() < defaults.maxPositionSize());
        assertTrue(conservative.maxDailyLoss() < defaults.maxDailyLoss());
    }

    @Test
    void getLimits_ShouldReturnConfiguredLimits() {
        RiskManager.RiskLimits limits = riskManager.getLimits();

        assertSame(testLimits, limits);
        assertEquals(1000, limits.maxOrderSize());
    }

    private Order createOrder(Symbol symbol, OrderSide side, long quantity, long price) {
        Order order = new Order();
        order.setSymbol(symbol);
        order.setSide(side);
        order.setQuantity(quantity);
        order.setPrice(price);
        order.setType(OrderType.LIMIT);
        return order;
    }
}
