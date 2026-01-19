package com.hft.core.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionTest {
    private Position position;
    private Symbol symbol;

    @BeforeEach
    void setUp() {
        symbol = new Symbol("AAPL", Exchange.ALPACA);
        position = new Position(symbol);
    }

    @Test
    void shouldStartFlat() {
        assertTrue(position.isFlat());
        assertFalse(position.isLong());
        assertFalse(position.isShort());
        assertEquals(0, position.getQuantity());
    }

    @Test
    void shouldOpenLongPosition() {
        Trade trade = createTrade(OrderSide.BUY, 100, 15000);
        position.applyTrade(trade);

        assertTrue(position.isLong());
        assertFalse(position.isShort());
        assertFalse(position.isFlat());
        assertEquals(100, position.getQuantity());
        assertEquals(15000, position.getAverageEntryPrice());
    }

    @Test
    void shouldOpenShortPosition() {
        Trade trade = createTrade(OrderSide.SELL, 100, 15000);
        position.applyTrade(trade);

        assertTrue(position.isShort());
        assertFalse(position.isLong());
        assertEquals(-100, position.getQuantity());
        assertEquals(15000, position.getAverageEntryPrice());
    }

    @Test
    void shouldAddToLongPosition() {
        position.applyTrade(createTrade(OrderSide.BUY, 100, 15000));
        position.applyTrade(createTrade(OrderSide.BUY, 50, 15200));

        assertEquals(150, position.getQuantity());
        // Average = (100*15000 + 50*15200) / 150 = 15066.67
        assertTrue(position.getAverageEntryPrice() > 15000);
        assertTrue(position.getAverageEntryPrice() < 15200);
    }

    @Test
    void shouldReduceLongPosition() {
        position.applyTrade(createTrade(OrderSide.BUY, 100, 15000));
        position.applyTrade(createTrade(OrderSide.SELL, 30, 15100));

        assertEquals(70, position.getQuantity());
        assertEquals(15000, position.getAverageEntryPrice()); // Entry price unchanged
        assertTrue(position.getRealizedPnl() > 0); // Profitable close
    }

    @Test
    void shouldCloseLongPosition() {
        position.applyTrade(createTrade(OrderSide.BUY, 100, 15000));
        position.applyTrade(createTrade(OrderSide.SELL, 100, 15500));

        assertTrue(position.isFlat());
        assertEquals(0, position.getQuantity());
        assertTrue(position.getRealizedPnl() > 0);
    }

    @Test
    void shouldReverseLongToShort() {
        position.applyTrade(createTrade(OrderSide.BUY, 100, 15000));
        position.applyTrade(createTrade(OrderSide.SELL, 150, 15100));

        assertTrue(position.isShort());
        assertEquals(-50, position.getQuantity());
        assertEquals(15100, position.getAverageEntryPrice()); // New entry price
        assertTrue(position.getRealizedPnl() > 0); // Profit from closing long
    }

    @Test
    void shouldCalculateUnrealizedPnl() {
        position.applyTrade(createTrade(OrderSide.BUY, 100, 15000));
        position.updateMarketValue(15500);

        // P&L = (15500 - 15000) * 100 = 50000 (cents)
        long expectedUnrealizedPnl = (15500 - 15000) * 100;
        assertEquals(expectedUnrealizedPnl, position.getUnrealizedPnl());
    }

    @Test
    void shouldCalculateUnrealizedPnlForShort() {
        position.applyTrade(createTrade(OrderSide.SELL, 100, 15000));
        position.updateMarketValue(14500);

        // Short position profits when price goes down
        // P&L = (14500 - 15000) * -100 = 50000 (cents profit)
        long expectedUnrealizedPnl = (14500 - 15000) * -100;
        assertEquals(expectedUnrealizedPnl, position.getUnrealizedPnl());
    }

    @Test
    void shouldTrackMaxDrawdown() {
        position.applyTrade(createTrade(OrderSide.BUY, 100, 15000));
        position.updateMarketValue(14000); // Price drops

        assertTrue(position.getMaxDrawdown() < 0);
    }

    @Test
    void shouldResetPosition() {
        position.applyTrade(createTrade(OrderSide.BUY, 100, 15000));
        position.updateMarketValue(15500);
        position.reset();

        assertTrue(position.isFlat());
        assertEquals(0, position.getRealizedPnl());
        assertEquals(0, position.getUnrealizedPnl());
    }

    private Trade createTrade(OrderSide side, long quantity, long price) {
        Trade trade = new Trade();
        trade.setSymbol(symbol);
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setExecutedAt(System.nanoTime());
        return trade;
    }
}
