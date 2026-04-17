package com.hft.api.dto;

import java.util.List;

/**
 * DTO for account balance information from an exchange.
 */
public record AccountBalanceDto(
    String exchange,
    List<BalanceEntry> balances
) {
    public record BalanceEntry(
        String asset,
        String free,
        String locked,
        String total
    ) {}
}
