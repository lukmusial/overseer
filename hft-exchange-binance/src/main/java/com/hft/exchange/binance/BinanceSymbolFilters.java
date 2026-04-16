package com.hft.exchange.binance;

/**
 * Parsed LOT_SIZE, PRICE_FILTER, and NOTIONAL data for a Binance symbol.
 * Used to validate and round order quantities/prices before submission.
 *
 * <p>Binance rejects orders that don't conform to these filters with error -1013.
 *
 * @param lotStepSize    quantity step size as a scaled long (8 decimals), e.g. 1000 = 0.00001
 * @param lotMinQty      minimum quantity as a scaled long (8 decimals)
 * @param priceTickSize  price tick size as a scaled long (8 decimals), e.g. 100 = 0.000001
 * @param minNotional    minimum order value in quote currency (8 decimals), e.g. 500000000 = $5
 */
public record BinanceSymbolFilters(
        long lotStepSize,
        long lotMinQty,
        long priceTickSize,
        long minNotional
) {
    /** Default filters when symbol info is unavailable — no rounding applied. */
    public static final BinanceSymbolFilters DEFAULT = new BinanceSymbolFilters(1, 0, 1, 0);

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

    /**
     * Returns true if the order meets the minimum notional value (qty * price).
     * Both quantity and price must be in 8-decimal scaled format.
     */
    public boolean meetsMinNotional(long quantity, long price) {
        if (minNotional <= 0) return true;
        // notional = qty * price / scale (both are 8-decimal, so divide by 10^8)
        long notional = quantity / 100 * price / 1_000_000; // avoid overflow
        return notional >= minNotional;
    }

    /**
     * Validates the order is submittable: quantity is rounded, meets minQty,
     * and meets minNotional.
     *
     * @return null if valid, or error message if invalid
     */
    public String validate(long quantity, long price) {
        long rounded = roundQuantity(quantity);
        if (rounded <= 0) {
            return "Quantity too small (rounds to 0 after LOT_SIZE step)";
        }
        if (!meetsMinQuantity(rounded)) {
            return "Quantity " + rounded + " below minimum " + lotMinQty;
        }
        if (!meetsMinNotional(rounded, price)) {
            return "Order notional value below minimum";
        }
        return null;
    }
}
