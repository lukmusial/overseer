package com.hft.api.dto;

/**
 * DTO for order markers on the chart.
 */
public record OrderMarkerDto(
        long time,          // Unix timestamp in seconds
        double price,
        String side,        // BUY or SELL
        long quantity,
        String status,      // FILLED, CANCELLED, etc.
        String strategyId,
        String orderId
) {}
