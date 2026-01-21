package com.hft.exchange.alpaca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Alpaca asset response.
 * Represents a tradable asset from the Alpaca API.
 */
public record AlpacaAsset(
        String id,
        @JsonProperty("class") String assetClass,
        String exchange,
        String symbol,
        String name,
        String status,
        boolean tradable,
        boolean marginable,
        boolean shortable,
        @JsonProperty("easy_to_borrow") boolean easyToBorrow,
        boolean fractionable,
        @JsonProperty("min_order_size") String minOrderSize,
        @JsonProperty("min_trade_increment") String minTradeIncrement,
        @JsonProperty("price_increment") String priceIncrement
) {
    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }

    public boolean isEquity() {
        return "us_equity".equalsIgnoreCase(assetClass);
    }

    public boolean isCrypto() {
        return "crypto".equalsIgnoreCase(assetClass);
    }
}
