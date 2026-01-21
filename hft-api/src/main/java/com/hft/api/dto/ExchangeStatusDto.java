package com.hft.api.dto;

/**
 * DTO for exchange connectivity status.
 */
public record ExchangeStatusDto(
        String exchange,
        String name,
        boolean connected,
        boolean authenticated,
        Long lastHeartbeat,
        String errorMessage
) {
    public static ExchangeStatusDto connected(String exchange, String name, boolean authenticated) {
        return new ExchangeStatusDto(exchange, name, true, authenticated, System.currentTimeMillis(), null);
    }

    public static ExchangeStatusDto disconnected(String exchange, String name, String error) {
        return new ExchangeStatusDto(exchange, name, false, false, null, error);
    }
}
