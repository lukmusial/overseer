package com.hft.exchange.binance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinanceSymbolFiltersTest {

    // BTCUSDT-like filters: stepSize=0.00001, minQty=0.00001, tickSize=0.01, minNotional=$5
    private static final BinanceSymbolFilters BTCUSDT_FILTERS =
            new BinanceSymbolFilters(1000, 1000, 1_000_000, 500_000_000L);

    @Test
    void roundQuantity_shouldRoundDownToStepSize() {
        // 0.01343175 BTC = 1343175 scaled -> rounds to 1343000
        assertEquals(1343000, BTCUSDT_FILTERS.roundQuantity(1343175));
        // Exact multiple unchanged
        assertEquals(1343000, BTCUSDT_FILTERS.roundQuantity(1343000));
        // 1.0 BTC = 100_000_000 scaled
        assertEquals(100_000_000, BTCUSDT_FILTERS.roundQuantity(100_000_000));
    }

    @Test
    void roundQuantity_shouldRoundToZeroWhenBelowStepSize() {
        // 628 < 1000 step -> rounds to 0
        assertEquals(0, BTCUSDT_FILTERS.roundQuantity(628));
    }

    @Test
    void roundQuantity_shouldNotRoundWhenStepSizeIsOne() {
        var filters = BinanceSymbolFilters.DEFAULT;
        assertEquals(1343175, filters.roundQuantity(1343175));
    }

    @Test
    void roundPrice_shouldRoundToTickSize() {
        // 67432.15123456 = 6743215123456L -> rounds to 6743215000000
        assertEquals(6743215000000L, BTCUSDT_FILTERS.roundPrice(6743215123456L));
    }

    @Test
    void meetsMinQuantity_shouldEnforceMinimum() {
        assertTrue(BTCUSDT_FILTERS.meetsMinQuantity(1000));
        assertTrue(BTCUSDT_FILTERS.meetsMinQuantity(2000));
        assertFalse(BTCUSDT_FILTERS.meetsMinQuantity(999));
        assertFalse(BTCUSDT_FILTERS.meetsMinQuantity(0));
    }

    @Test
    void meetsMinNotional_shouldEnforceMinimumOrderValue() {
        // 0.00002 BTC at $70,000 = $1.40 -> below $5 min
        assertFalse(BTCUSDT_FILTERS.meetsMinNotional(2000, 7_000_000_000_000L));

        // 0.001 BTC at $70,000 = $70 -> above $5 min
        assertTrue(BTCUSDT_FILTERS.meetsMinNotional(100_000, 7_000_000_000_000L));
    }

    @Test
    void validate_shouldRejectQuantityThatRoundsToZero() {
        String error = BTCUSDT_FILTERS.validate(628, 7_000_000_000_000L);
        assertNotNull(error);
        assertTrue(error.contains("rounds to 0"));
    }

    @Test
    void validate_shouldRejectBelowMinQty() {
        // qty=500, step=1000 -> rounds to 0
        String error = BTCUSDT_FILTERS.validate(500, 7_000_000_000_000L);
        assertNotNull(error);
    }

    @Test
    void validate_shouldRejectBelowMinNotional() {
        // 0.00001 BTC (1000 scaled) at $70,000 = $0.70 < $5 min
        String error = BTCUSDT_FILTERS.validate(1000, 7_000_000_000_000L);
        assertNotNull(error);
        assertTrue(error.contains("notional"));
    }

    @Test
    void validate_shouldAcceptValidOrder() {
        // 0.01 BTC (1_000_000 scaled) at $70,000 = $700 > $5 min
        String error = BTCUSDT_FILTERS.validate(1_000_000, 7_000_000_000_000L);
        assertNull(error);
    }
}
