package com.hft.exchange.binance;

/**
 * Parsed LOT_SIZE and PRICE_FILTER data for a Binance symbol.
 * Used to round order quantities and prices to valid step sizes before submission.
 *
 * <p>Binance rejects orders that don't conform to these filters with error -1013
 * (Filter failure: LOT_SIZE) or -1013 (Filter failure: PRICE_FILTER).
 *
 * @param lotStepSize    quantity step size as a scaled long (8 decimals), e.g. 1000 = 0.00001
 * @param lotMinQty      minimum quantity as a scaled long (8 decimals)
 * @param priceTickSize  price tick size as a scaled long (8 decimals), e.g. 100 = 0.000001
 */
public record BinanceSymbolFilters(
        long lotStepSize,
        long lotMinQty,
        long priceTickSize
) {
    /** Default filters when symbol info is unavailable — no rounding applied. */
    public static final BinanceSymbolFilters DEFAULT = new BinanceSymbolFilters(1, 0, 1);

    /**
     * Rounds a quantity down to the nearest multiple of the lot step size.
     *
     * @param quantity the raw quantity (scaled to 8 decimals)
     * @return the rounded quantity
     */
    public long roundQuantity(long quantity) {
        if (lotStepSize <= 1) return quantity;
        return (quantity / lotStepSize) * lotStepSize;
    }

    /**
     * Rounds a price to the nearest multiple of the price tick size.
     *
     * @param price the raw price (scaled to 8 decimals)
     * @return the rounded price
     */
    public long roundPrice(long price) {
        if (priceTickSize <= 1) return price;
        return (price / priceTickSize) * priceTickSize;
    }

    /**
     * Returns true if the quantity meets the minimum requirement.
     */
    public boolean meetsMinQuantity(long quantity) {
        return quantity >= lotMinQty;
    }
}
