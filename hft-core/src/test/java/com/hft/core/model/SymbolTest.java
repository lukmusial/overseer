package com.hft.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SymbolTest {

    @Test
    void shouldCreateSymbol() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);

        assertEquals("AAPL", symbol.getTicker());
        assertEquals(Exchange.ALPACA, symbol.getExchange());
        assertEquals(AssetClass.STOCK, symbol.getAssetClass());
    }

    @Test
    void shouldUppercaseTicker() {
        Symbol symbol = new Symbol("btcusdt", Exchange.BINANCE);

        assertEquals("BTCUSDT", symbol.getTicker());
    }

    @Test
    void shouldGetFullSymbol() {
        Symbol symbol = new Symbol("AAPL", Exchange.ALPACA);

        assertEquals("alpaca:AAPL", symbol.getFullSymbol());
    }

    @Test
    void shouldBeEqualWithSameTickerAndExchange() {
        Symbol symbol1 = new Symbol("AAPL", Exchange.ALPACA);
        Symbol symbol2 = new Symbol("AAPL", Exchange.ALPACA);

        assertEquals(symbol1, symbol2);
        assertEquals(symbol1.hashCode(), symbol2.hashCode());
    }

    @Test
    void shouldNotBeEqualWithDifferentTicker() {
        Symbol symbol1 = new Symbol("AAPL", Exchange.ALPACA);
        Symbol symbol2 = new Symbol("GOOGL", Exchange.ALPACA);

        assertNotEquals(symbol1, symbol2);
    }

    @Test
    void shouldNotBeEqualWithDifferentExchange() {
        Symbol symbol1 = new Symbol("BTCUSDT", Exchange.BINANCE);
        Symbol symbol2 = new Symbol("BTCUSDT", Exchange.BINANCE_TESTNET);

        assertNotEquals(symbol1, symbol2);
    }

    @Test
    void shouldThrowOnNullTicker() {
        assertThrows(NullPointerException.class, () -> new Symbol(null, Exchange.ALPACA));
    }

    @Test
    void shouldThrowOnNullExchange() {
        assertThrows(NullPointerException.class, () -> new Symbol("AAPL", null));
    }

    @Test
    void shouldWorkInHashSet() {
        Symbol symbol1 = new Symbol("AAPL", Exchange.ALPACA);
        Symbol symbol2 = new Symbol("AAPL", Exchange.ALPACA);

        java.util.Set<Symbol> set = new java.util.HashSet<>();
        set.add(symbol1);
        set.add(symbol2);

        assertEquals(1, set.size());
        assertTrue(set.contains(symbol1));
        assertTrue(set.contains(symbol2));
    }
}
