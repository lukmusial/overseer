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
    /**
     * Normalizes timestamp to milliseconds.
     * Real exchange quotes may use nanoseconds, stub quotes use milliseconds.
     * Values > 1e15 are assumed to be nanoseconds and converted to milliseconds.
     */
    private static long normalizeTimestamp(long ts) {
        // If timestamp is larger than year 33658 in milliseconds, it's likely nanoseconds
        if (ts > 1_000_000_000_000_000L) {
            return ts / 1_000_000;  // Convert nanos to millis
        }
        return ts;
    }

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
                normalizeTimestamp(quote.getTimestamp())
        );
    }
}
