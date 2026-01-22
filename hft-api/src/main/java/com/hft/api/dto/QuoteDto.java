package com.hft.api.dto;

import com.hft.core.model.Quote;

/**
 * DTO for market quote information.
 */
public record QuoteDto(
        String symbol,
        String exchange,
        double bidPrice,
        double askPrice,
        long bidSize,
        long askSize,
        double midPrice,
        double spread,
        long timestamp
) {
    public static QuoteDto from(Quote quote) {
        return new QuoteDto(
                quote.getSymbol().getTicker(),
                quote.getSymbol().getExchange().name(),
                quote.getBidPriceAsDouble(),
                quote.getAskPriceAsDouble(),
                quote.getBidSize(),
                quote.getAskSize(),
                (quote.getBidPriceAsDouble() + quote.getAskPriceAsDouble()) / 2,
                quote.getAskPriceAsDouble() - quote.getBidPriceAsDouble(),
                quote.getTimestamp()
        );
    }
}
