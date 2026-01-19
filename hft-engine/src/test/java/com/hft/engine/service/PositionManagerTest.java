package com.hft.engine.service;

import com.hft.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class PositionManagerTest {

    private PositionManager positionManager;
    private Symbol testSymbol;

    @BeforeEach
    void setUp() {
        positionManager = new PositionManager();
        testSymbol = new Symbol("AAPL", Exchange.ALPACA);
    }

    @Test
    void getOrCreatePosition_ShouldCreateNewPosition() {
        Position position = positionManager.getOrCreatePosition(testSymbol);

        assertNotNull(position);
        assertEquals(testSymbol, position.getSymbol());
        assertEquals(0, position.getQuantity());
    }

    @Test
    void getOrCreatePosition_ShouldReturnExistingPosition() {
        Position first = positionManager.getOrCreatePosition(testSymbol);
        Position second = positionManager.getOrCreatePosition(testSymbol);

        assertSame(first, second);
    }

    @Test
    void getPosition_WhenNotExists_ShouldReturnNull() {
        Position position = positionManager.getPosition(testSymbol);
        assertNull(position);
    }

    @Test
    void applyTrade_BuyTrade_ShouldIncreasePosition() {
        Trade buyTrade = createTrade(testSymbol, OrderSide.BUY, 100, 15000L);

        positionManager.applyTrade(buyTrade);

        Position position = positionManager.getPosition(testSymbol);
        assertNotNull(position);
        assertEquals(100, position.getQuantity());
        assertTrue(position.isLong());
    }

    @Test
    void applyTrade_SellTrade_ShouldDecreasePosition() {
        // First buy
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));

        // Then sell some
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.SELL, 60, 15100L));

        Position position = positionManager.getPosition(testSymbol);
        assertEquals(40, position.getQuantity());
    }

    @Test
    void applyTrade_SellMoreThanOwned_ShouldGoShort() {
        // Buy 50
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 50, 15000L));

        // Sell 100 (going short 50)
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.SELL, 100, 15100L));

        Position position = positionManager.getPosition(testSymbol);
        assertEquals(-50, position.getQuantity());
        assertTrue(position.isShort());
    }

    @Test
    void applyTrade_ShouldCalculateRealizedPnl() {
        // Buy 100 @ $150
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));

        // Sell 50 @ $160 (profit of $10/share * 50 = $500)
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.SELL, 50, 16000L));

        Position position = positionManager.getPosition(testSymbol);
        assertEquals(50000L, position.getRealizedPnl()); // $500 in cents
    }

    @Test
    void updateMarketValue_ShouldCalculateUnrealizedPnl() {
        // Buy 100 @ $150
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));

        // Market price moves to $160
        positionManager.updateMarketValue(testSymbol, 16000L);

        Position position = positionManager.getPosition(testSymbol);
        assertEquals(100000L, position.getUnrealizedPnl()); // $1000 in cents (100 shares * $10 profit)
    }

    @Test
    void getAllPositions_ShouldReturnAllPositions() {
        Symbol symbol2 = new Symbol("GOOGL", Exchange.ALPACA);

        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        positionManager.applyTrade(createTrade(symbol2, OrderSide.BUY, 50, 200000L));

        Collection<Position> positions = positionManager.getAllPositions();
        assertEquals(2, positions.size());
    }

    @Test
    void getActivePositions_ShouldExcludeFlatPositions() {
        Symbol symbol2 = new Symbol("GOOGL", Exchange.ALPACA);

        // Create AAPL position and flatten it
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.SELL, 100, 15100L));

        // Create GOOGL position and keep it open
        positionManager.applyTrade(createTrade(symbol2, OrderSide.BUY, 50, 200000L));

        Collection<Position> activePositions = positionManager.getActivePositions();
        assertEquals(1, activePositions.size());
        assertEquals(symbol2, activePositions.iterator().next().getSymbol());
    }

    @Test
    void getTotalRealizedPnl_ShouldSumAcrossAllPositions() {
        Symbol symbol2 = new Symbol("GOOGL", Exchange.ALPACA);

        // AAPL: Buy @ $150, sell @ $160 -> $10 profit per share
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.SELL, 100, 16000L));

        // GOOGL: Buy @ $2000, sell @ $2100 -> $100 profit per share
        positionManager.applyTrade(createTrade(symbol2, OrderSide.BUY, 10, 200000L));
        positionManager.applyTrade(createTrade(symbol2, OrderSide.SELL, 10, 210000L));

        long totalRealized = positionManager.getTotalRealizedPnl();
        assertEquals(200000L, totalRealized); // $1000 + $1000 = $2000 in cents
    }

    @Test
    void getTotalUnrealizedPnl_ShouldSumAcrossAllPositions() {
        Symbol symbol2 = new Symbol("GOOGL", Exchange.ALPACA);

        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        positionManager.applyTrade(createTrade(symbol2, OrderSide.BUY, 10, 200000L));

        // Update market prices
        positionManager.updateMarketValue(testSymbol, 16000L); // +$1000
        positionManager.updateMarketValue(symbol2, 210000L);   // +$1000

        long totalUnrealized = positionManager.getTotalUnrealizedPnl();
        assertEquals(200000L, totalUnrealized); // $2000 in cents
    }

    @Test
    void getTotalPnl_ShouldSumRealizedAndUnrealized() {
        // Buy and partial sell with profit
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.SELL, 50, 16000L)); // $500 realized

        // Mark remaining position to market
        positionManager.updateMarketValue(testSymbol, 16000L); // $500 unrealized

        long totalPnl = positionManager.getTotalPnl();
        assertEquals(100000L, totalPnl); // $1000 total in cents
    }

    @Test
    void getNetExposure_ShouldSumAbsoluteMarketValues() {
        Symbol symbol2 = new Symbol("GOOGL", Exchange.ALPACA);

        // Long position
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        positionManager.updateMarketValue(testSymbol, 15000L);

        // Short position
        positionManager.applyTrade(createTrade(symbol2, OrderSide.SELL, 10, 200000L));
        positionManager.updateMarketValue(symbol2, 200000L);

        long netExposure = positionManager.getNetExposure();
        assertTrue(netExposure > 0);
    }

    @Test
    void getGrossExposure_ShouldSeparateLongAndShort() {
        Symbol symbol2 = new Symbol("GOOGL", Exchange.ALPACA);

        // Long AAPL: 100 shares @ $150 = $15,000
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        positionManager.updateMarketValue(testSymbol, 15000L);

        // Short GOOGL: 10 shares @ $2000 = $20,000
        positionManager.applyTrade(createTrade(symbol2, OrderSide.SELL, 10, 200000L));
        positionManager.updateMarketValue(symbol2, 200000L);

        PositionManager.GrossExposure grossExposure = positionManager.getGrossExposure();

        assertTrue(grossExposure.longExposure() > 0);
        assertTrue(grossExposure.shortExposure() > 0);
    }

    @Test
    void positionListener_ShouldBeNotifiedOnTrade() {
        int[] callCount = {0};
        positionManager.addPositionListener(position -> callCount[0]++);

        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));

        assertEquals(1, callCount[0]);
    }

    @Test
    void positionListener_ShouldBeNotifiedOnMarketUpdate() {
        int[] callCount = {0};
        positionManager.addPositionListener(position -> callCount[0]++);

        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        callCount[0] = 0; // Reset after trade notification

        positionManager.updateMarketValue(testSymbol, 16000L);

        assertEquals(1, callCount[0]);
    }

    @Test
    void removePositionListener_ShouldStopNotifications() {
        int[] callCount = {0};
        java.util.function.Consumer<Position> listener = position -> callCount[0]++;

        positionManager.addPositionListener(listener);
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        assertEquals(1, callCount[0]);

        positionManager.removePositionListener(listener);
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 50, 15000L));
        assertEquals(1, callCount[0]); // Should not increase
    }

    @Test
    void clear_ShouldResetAllPositions() {
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.SELL, 50, 16000L));

        positionManager.clear();

        assertEquals(0, positionManager.getAllPositions().size());
        assertEquals(0, positionManager.getTotalRealizedPnl());
        assertEquals(0, positionManager.getTotalUnrealizedPnl());
    }

    @Test
    void getSnapshot_ShouldCaptureCurrentState() {
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        positionManager.updateMarketValue(testSymbol, 16000L);

        PositionManager.PositionSnapshot snapshot = positionManager.getSnapshot();

        assertEquals(1, snapshot.totalPositions());
        assertEquals(1, snapshot.activePositions());
        assertEquals(0, snapshot.realizedPnl());
        assertEquals(100000L, snapshot.unrealizedPnl()); // $1000
        assertEquals(100000L, snapshot.totalPnl());
    }

    private Trade createTrade(Symbol symbol, OrderSide side, long quantity, long price) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setExecutedAt(System.nanoTime());
        return trade;
    }
}
