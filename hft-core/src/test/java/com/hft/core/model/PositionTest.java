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

    // --- Crypto / quantityScale tests ---

    @Test
    void shouldHandleFractionalCryptoBuy() {
        // 0.5 BTC at $50,000.00 (priceScale=100_000_000, quantityScale=100_000_000)
        Symbol btcSymbol = new Symbol("BTCUSDT", Exchange.BINANCE);
        Position cryptoPos = new Position(btcSymbol);
        cryptoPos.setPriceScale(100_000_000);
        cryptoPos.setQuantityScale(100_000_000);

        long price = 5_000_000_000_000L; // $50,000.00 in satoshi-scale
        long qty = 50_000_000L;          // 0.5 BTC

        Trade trade = new Trade();
        trade.setSymbol(btcSymbol);
        trade.setSide(OrderSide.BUY);
        trade.setQuantity(qty);
        trade.setPrice(price);
        trade.setPriceScale(100_000_000);
        trade.setExecutedAt(System.nanoTime());

        cryptoPos.applyTrade(trade);

        assertEquals(50_000_000L, cryptoPos.getQuantity());
        assertEquals(0.5, cryptoPos.getQuantityAsDouble(), 1e-9);
        assertEquals(price, cryptoPos.getAverageEntryPrice());
        // Market value = price * qty / priceScale / quantityScale = $25,000
        assertEquals(25_000, cryptoPos.getMarketValue());
    }

    @Test
    void shouldCalculateCryptoPnlWithoutOverflow() {
        // Buy 0.5 BTC at $50,000 then price rises to $60,000
        Symbol btcSymbol = new Symbol("BTCUSDT", Exchange.BINANCE);
        Position cryptoPos = new Position(btcSymbol);
        cryptoPos.setPriceScale(100_000_000);
        cryptoPos.setQuantityScale(100_000_000);

        long buyPrice = 5_000_000_000_000L;  // $50,000 in 1e8 scale
        long qty = 50_000_000L;               // 0.5 BTC
        long newPrice = 6_000_000_000_000L;   // $60,000 in 1e8 scale

        Trade trade = new Trade();
        trade.setSymbol(btcSymbol);
        trade.setSide(OrderSide.BUY);
        trade.setQuantity(qty);
        trade.setPrice(buyPrice);
        trade.setPriceScale(100_000_000);
        trade.setExecutedAt(System.nanoTime());

        cryptoPos.applyTrade(trade);
        cryptoPos.updateMarketValue(newPrice);

        // Unrealized P&L = (60000 - 50000) * 0.5 = $5,000
        // In priceScale units: 5000 * 100_000_000 = 500_000_000_000
        long expectedPnl = (long) ((double) (newPrice - buyPrice) * qty / 100_000_000);
        assertEquals(expectedPnl, cryptoPos.getUnrealizedPnl());
        // Verify it's approximately $5,000 worth
        double pnlDollars = (double) cryptoPos.getUnrealizedPnl() / 100_000_000;
        assertEquals(5_000.0, pnlDollars, 0.01);
    }

    @Test
    void shouldCloseCryptoPositionWithRealizedPnl() {
        Symbol btcSymbol = new Symbol("BTCUSDT", Exchange.BINANCE);
        Position cryptoPos = new Position(btcSymbol);
        cryptoPos.setPriceScale(100_000_000);
        cryptoPos.setQuantityScale(100_000_000);

        long buyPrice = 5_000_000_000_000L;   // $50,000
        long sellPrice = 5_500_000_000_000L;   // $55,000
        long qty = 50_000_000L;                // 0.5 BTC

        Trade buy = new Trade();
        buy.setSymbol(btcSymbol);
        buy.setSide(OrderSide.BUY);
        buy.setQuantity(qty);
        buy.setPrice(buyPrice);
        buy.setPriceScale(100_000_000);
        buy.setExecutedAt(System.nanoTime());

        Trade sell = new Trade();
        sell.setSymbol(btcSymbol);
        sell.setSide(OrderSide.SELL);
        sell.setQuantity(qty);
        sell.setPrice(sellPrice);
        sell.setPriceScale(100_000_000);
        sell.setExecutedAt(System.nanoTime());

        cryptoPos.applyTrade(buy);
        cryptoPos.applyTrade(sell);

        assertTrue(cryptoPos.isFlat());
        // Realized P&L = ($55,000 - $50,000) * 0.5 = $2,500
        double pnlDollars = (double) cryptoPos.getRealizedPnl() / 100_000_000;
        assertEquals(2_500.0, pnlDollars, 0.01);
    }

    @Test
    void shouldDefaultQuantityScaleToOne() {
        // Existing stock positions should work unchanged
        assertEquals(1, position.getQuantityScale());

        Trade trade = createTrade(OrderSide.BUY, 100, 15000);
        position.applyTrade(trade);

        assertEquals(100, position.getQuantity());
        // Market value = 15000 * 100 / 100 / 1 = 15000
        assertEquals(15000, position.getMarketValue());
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
