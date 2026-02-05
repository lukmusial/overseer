package com.hft.api.dto;

import com.hft.core.model.Position;

public record PositionDto(
        String symbol,
        String exchange,
        long quantity,
        long averageEntryPrice,
        long marketPrice,
        long marketValue,
        long realizedPnl,
        long unrealizedPnl,
        long maxDrawdown,
        int priceScale,
        boolean isLong,
        boolean isShort,
        boolean isFlat
) {
    public static PositionDto from(Position position) {
        // Calculate market price from market value and quantity
        long marketPrice = position.getQuantity() != 0
                ? position.getMarketValue() / position.getQuantity()
                : 0;
        return new PositionDto(
                position.getSymbol() != null ? position.getSymbol().getTicker() : null,
                position.getSymbol() != null ? position.getSymbol().getExchange().name() : null,
                position.getQuantity(),
                position.getAverageEntryPrice(),
                marketPrice,
                position.getMarketValue(),
                position.getRealizedPnl(),
                position.getUnrealizedPnl(),
                position.getMaxDrawdown(),
                position.getPriceScale(),
                position.isLong(),
                position.isShort(),
                position.isFlat()
        );
    }
}
