package com.hft.api.dto;

/**
 * DTO for OHLC candlestick data.
 */
public record CandleDto(
        long time,      // Unix timestamp in seconds
        double open,
        double high,
        double low,
        double close,
        long volume
) {}
