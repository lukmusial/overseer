package com.hft.api.dto;

/**
 * DTO for exchange connectivity status.
 */
public record ExchangeStatusDto(
        String exchange,
        String name,
        String mode,
        boolean connected,
        boolean authenticated,
        Long lastHeartbeat,
        String errorMessage
) {
    public static ExchangeStatusDto connected(String exchange, String name, String mode, boolean authenticated) {
        return new ExchangeStatusDto(exchange, name, mode, true, authenticated, System.currentTimeMillis(), null);
    }

    public static ExchangeStatusDto disconnected(String exchange, String name, String mode, String error) {
        return new ExchangeStatusDto(exchange, name, mode, false, false, null, error);
    }
}
