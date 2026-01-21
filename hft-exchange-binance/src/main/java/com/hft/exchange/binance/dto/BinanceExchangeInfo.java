package com.hft.exchange.binance.dto;

import java.util.List;

/**
 * DTO for Binance exchange information response.
 */
public record BinanceExchangeInfo(
        String timezone,
        long serverTime,
        List<BinanceSymbol> symbols
) {}
