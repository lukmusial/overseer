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
    void getNetExposure_ShouldCalculateLongMinusShort() {
        Symbol symbol2 = new Symbol("GOOGL", Exchange.ALPACA);

        // Long AAPL: 100 shares @ $150 = $15,000 market value = 15000 cents
        positionManager.applyTrade(createTrade(testSymbol, OrderSide.BUY, 100, 15000L));
        positionManager.updateMarketValue(testSymbol, 15000L);

        // Short GOOGL: 10 shares @ $2000 = $20,000 market value = 20000 cents
        positionManager.applyTrade(createTrade(symbol2, OrderSide.SELL, 10, 200000L));
        positionManager.updateMarketValue(symbol2, 200000L);

        // Net exposure = long - short = 15000 - 20000 = -5000 (net short)
        long netExposure = positionManager.getNetExposure();
        assertEquals(-5000L, netExposure);
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

    @Test
    void getTotalPnlCents_shouldNormalizeAcrossDifferentPriceScales() {
        // AAPL: stock with priceScale=100 (cents)
        // Buy 100 shares @ $150.00 (15000 in scale 100), sell @ $160.00 (16000)
        // Realized P&L = (16000 - 15000) * 100 = 100,000 (in scale 100 = $1,000.00)
        Symbol aapl = new Symbol("AAPL", Exchange.ALPACA);
        positionManager.applyTrade(createTrade(aapl, OrderSide.BUY, 100, 15000L, 100));
        positionManager.applyTrade(createTrade(aapl, OrderSide.SELL, 100, 16000L, 100));

        // BTCUSDT: crypto with priceScale=100_000_000 (satoshi-like)
        // Buy 1 BTC @ $50,000.00 (5_000_000_000_000L in scale 100_000_000), sell @ $51,000.00
        // Realized P&L = (5_100_000_000_000 - 5_000_000_000_000) * 1 = 100_000_000_000 (in scale 100_000_000 = $1,000.00)
        Symbol btc = new Symbol("BTCUSDT", Exchange.BINANCE);
        positionManager.applyTrade(createTrade(btc, OrderSide.BUY, 1, 5_000_000_000_000L, 100_000_000));
        positionManager.applyTrade(createTrade(btc, OrderSide.SELL, 1, 5_100_000_000_000L, 100_000_000));

        // Both positions are flat with realized P&L
        Position aaplPos = positionManager.getPosition(aapl);
        Position btcPos = positionManager.getPosition(btc);
        assertTrue(aaplPos.isFlat());
        assertTrue(btcPos.isFlat());

        // Raw P&L values are in different scales — not directly comparable
        long aaplRawPnl = aaplPos.getRealizedPnl();
        long btcRawPnl = btcPos.getRealizedPnl();
        assertEquals(100_000L, aaplRawPnl);               // $1,000 in scale 100
        assertEquals(100_000_000_000L, btcRawPnl);         // $1,000 in scale 100_000_000

        // getTotalPnlCents() should normalize both to cents (scale 100)
        // AAPL: 100,000 * 100 / 100 = 100,000 cents ($1,000)
        // BTC:  100,000,000,000 * 100 / 100,000,000 = 100,000 cents ($1,000)
        long totalPnlCents = positionManager.getTotalPnlCents();
        assertEquals(200_000L, totalPnlCents, "Total P&L should be $2,000 in cents");

        long totalRealizedCents = positionManager.getTotalRealizedPnlCents();
        assertEquals(200_000L, totalRealizedCents, "Total realized P&L should be $2,000 in cents");

        long totalUnrealizedCents = positionManager.getTotalUnrealizedPnlCents();
        assertEquals(0L, totalUnrealizedCents, "No unrealized P&L for flat positions");
    }

    @Test
    void getTotalUnrealizedPnlCents_shouldNormalizeAcrossDifferentPriceScales() {
        // AAPL: stock with priceScale=100, buy and hold
        Symbol aapl = new Symbol("AAPL", Exchange.ALPACA);
        positionManager.applyTrade(createTrade(aapl, OrderSide.BUY, 100, 15000L, 100));
        positionManager.updateMarketValue(aapl, 16000L); // +$10/share * 100 = $1,000 unrealized

        // BTCUSDT: crypto with priceScale=100_000_000, buy and hold
        Symbol btc = new Symbol("BTCUSDT", Exchange.BINANCE);
        positionManager.applyTrade(createTrade(btc, OrderSide.BUY, 1, 5_000_000_000_000L, 100_000_000));
        positionManager.updateMarketValue(btc, 5_100_000_000_000L); // +$1,000 unrealized

        long totalUnrealizedCents = positionManager.getTotalUnrealizedPnlCents();
        assertEquals(200_000L, totalUnrealizedCents, "Total unrealized P&L should be $2,000 in cents");

        long totalPnlCents = positionManager.getTotalPnlCents();
        assertEquals(200_000L, totalPnlCents, "Total P&L should be $2,000 in cents");
    }

    private Trade createTrade(Symbol symbol, OrderSide side, long quantity, long price) {
        return createTrade(symbol, side, quantity, price, 100);
    }

    private Trade createTrade(Symbol symbol, OrderSide side, long quantity, long price, int priceScale) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setPriceScale(priceScale);
        trade.setExecutedAt(System.nanoTime());
        return trade;
    }
}
