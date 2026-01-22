package com.hft.api.dto;

import java.util.List;

/**
 * DTO containing all data needed for the candlestick chart.
 */
public record ChartDataDto(
        String symbol,
        String exchange,
        String interval,        // e.g., "1m", "5m", "1h", "1d"
        List<CandleDto> candles,
        List<OrderMarkerDto> orders,
        List<TriggerRangeDto> triggerRanges
) {}
