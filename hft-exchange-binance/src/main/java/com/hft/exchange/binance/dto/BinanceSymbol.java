package com.hft.exchange.binance.dto;

import java.util.List;

/**
 * DTO for Binance symbol information.
 */
public record BinanceSymbol(
        String symbol,
        String status,
        String baseAsset,
        int baseAssetPrecision,
        String quoteAsset,
        int quotePrecision,
        int quoteAssetPrecision,
        List<String> orderTypes,
        boolean icebergAllowed,
        boolean ocoAllowed,
        boolean isSpotTradingAllowed,
        boolean isMarginTradingAllowed
) {
    public boolean isTrading() {
        return "TRADING".equalsIgnoreCase(status);
    }
}
