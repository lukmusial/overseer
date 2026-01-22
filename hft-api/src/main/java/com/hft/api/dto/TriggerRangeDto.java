package com.hft.api.dto;

/**
 * DTO for strategy trigger price ranges.
 * Represents the price range where a strategy would trigger buy/sell signals.
 */
public record TriggerRangeDto(
        String strategyId,
        String strategyName,
        String type,            // Strategy type (momentum, meanreversion, etc.)
        String symbol,
        double currentPrice,
        Double buyTriggerLow,   // Price range where buy signals trigger
        Double buyTriggerHigh,
        Double sellTriggerLow,  // Price range where sell signals trigger
        Double sellTriggerHigh,
        String description      // Human-readable explanation
) {}
