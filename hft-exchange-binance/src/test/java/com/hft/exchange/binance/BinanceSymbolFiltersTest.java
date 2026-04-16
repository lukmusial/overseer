package com.hft.exchange.binance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinanceSymbolFiltersTest {

    @Test
    void roundQuantity_shouldRoundDownToStepSize() {
        // stepSize = 0.00001 (1000 in 8-decimal scale)
        var filters = new BinanceSymbolFilters(1000, 100000, 1);

        // 0.01343175 BTC = 1343175 scaled
        assertEquals(1343000, filters.roundQuantity(1343175));
        // Exact multiple unchanged
        assertEquals(1343000, filters.roundQuantity(1343000));
        // 1.0 BTC = 100_000_000 scaled
        assertEquals(100_000_000, filters.roundQuantity(100_000_000));
    }

    @Test
    void roundQuantity_shouldNotRoundWhenStepSizeIsOne() {
        var filters = BinanceSymbolFilters.DEFAULT;
        assertEquals(1343175, filters.roundQuantity(1343175));
    }

    @Test
    void roundPrice_shouldRoundToTickSize() {
        // tickSize = 0.01 (1_000_000 in 8-decimal scale)
        var filters = new BinanceSymbolFilters(1, 0, 1_000_000);

        // 67432.15123456 = 6743215123456L
        assertEquals(6743215000000L, filters.roundPrice(6743215123456L));
    }

    @Test
    void meetsMinQuantity_shouldEnforceMinimum() {
        // minQty = 0.001 (100_000 in 8-decimal scale)
        var filters = new BinanceSymbolFilters(1000, 100_000, 1);

        assertTrue(filters.meetsMinQuantity(100_000));
        assertTrue(filters.meetsMinQuantity(200_000));
        assertFalse(filters.meetsMinQuantity(99_999));
        assertFalse(filters.meetsMinQuantity(0));
    }
}
