package com.hft.persistence.chronicle;

import com.hft.core.model.Exchange;
import com.hft.core.model.Position;
import com.hft.core.model.Symbol;
import com.hft.persistence.PositionSnapshotStore.PositionSnapshot;
import net.openhft.chronicle.wire.SelfDescribingMarshallable;

/**
 * Chronicle Wire format for Position snapshot serialization.
 */
public class PositionSnapshotWire extends SelfDescribingMarshallable {
    private String ticker;
    private String exchange;
    private long quantity;
    private long averageEntryPrice;
    private long totalCost;
    private long realizedPnl;
    private long unrealizedPnl;
    private long currentPrice;
    private long marketValue;
    private int priceScale;
    private long openedAt;
    private long timestampNanos;

    public PositionSnapshotWire() {
    }

    public static PositionSnapshotWire from(Position position, long timestampNanos) {
        PositionSnapshotWire wire = new PositionSnapshotWire();
        wire.ticker = position.getSymbol().getTicker();
        wire.exchange = position.getSymbol().getExchange().name();
        wire.quantity = position.getQuantity();
        wire.averageEntryPrice = position.getAverageEntryPrice();
        wire.totalCost = position.getTotalCost();
        wire.realizedPnl = position.getRealizedPnl();
        wire.unrealizedPnl = position.getUnrealizedPnl();
        wire.currentPrice = position.getCurrentPrice();
        wire.marketValue = position.getMarketValue();
        wire.priceScale = position.getPriceScale();
        wire.openedAt = position.getOpenedAt();
        wire.timestampNanos = timestampNanos;
        return wire;
    }

    public PositionSnapshot toSnapshot() {
        return new PositionSnapshot(
                toSymbol(),
                quantity,
                averageEntryPrice,
                realizedPnl,
                unrealizedPnl,
                marketValue,
                timestampNanos,
                totalCost,
                currentPrice,
                priceScale,
                openedAt
        );
    }

    public Symbol toSymbol() {
        return new Symbol(ticker, Exchange.valueOf(exchange));
    }

    // Getters for index building
    public String getTicker() { return ticker; }
    public String getExchange() { return exchange; }
    public long getQuantity() { return quantity; }
    public long getAverageEntryPrice() { return averageEntryPrice; }
    public long getTotalCost() { return totalCost; }
    public long getRealizedPnl() { return realizedPnl; }
    public long getUnrealizedPnl() { return unrealizedPnl; }
    public long getCurrentPrice() { return currentPrice; }
    public long getMarketValue() { return marketValue; }
    public int getPriceScale() { return priceScale; }
    public long getOpenedAt() { return openedAt; }
    public long getTimestampNanos() { return timestampNanos; }
}
