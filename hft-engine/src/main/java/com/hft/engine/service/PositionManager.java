package com.hft.engine.service;

import com.hft.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages positions across all symbols.
 * Thread-safe position tracking with P&L calculations.
 */
public class PositionManager {
    private static final Logger log = LoggerFactory.getLogger(PositionManager.class);

    private final Map<Symbol, Position> positions;
    private final List<Consumer<Position>> positionListeners;

    // Aggregate P&L tracking
    private volatile long totalRealizedPnl;
    private volatile long totalUnrealizedPnl;

    public PositionManager() {
        this.positions = new ConcurrentHashMap<>();
        this.positionListeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Gets or creates a position for a symbol.
     */
    public Position getOrCreatePosition(Symbol symbol) {
        return positions.computeIfAbsent(symbol, Position::new);
    }

    /**
     * Gets position for a symbol (null if not exists).
     */
    public Position getPosition(Symbol symbol) {
        return positions.get(symbol);
    }

    /**
     * Applies a trade to the corresponding position.
     */
    public void applyTrade(Trade trade) {
        Position position = getOrCreatePosition(trade.getSymbol());

        long previousRealizedPnl = position.getRealizedPnl();
        position.applyTrade(trade);
        long newRealizedPnl = position.getRealizedPnl();

        // Update aggregate P&L
        totalRealizedPnl += (newRealizedPnl - previousRealizedPnl);

        log.debug("Trade applied: {} {} {} @ {} -> Position: {} qty, realized P&L: {}",
                trade.getSymbol(), trade.getSide(), trade.getQuantity(), trade.getPrice(),
                position.getQuantity(), position.getRealizedPnl());

        notifyListeners(position);
    }

    /**
     * Updates market value for a position.
     */
    public void updateMarketValue(Symbol symbol, long marketPrice) {
        Position position = positions.get(symbol);
        if (position != null && !position.isFlat()) {
            long previousUnrealized = position.getUnrealizedPnl();
            position.updateMarketValue(marketPrice);
            long newUnrealized = position.getUnrealizedPnl();

            // Update aggregate unrealized P&L
            totalUnrealizedPnl += (newUnrealized - previousUnrealized);

            notifyListeners(position);
        }
    }

    /**
     * Gets all positions.
     */
    public Collection<Position> getAllPositions() {
        return positions.values();
    }

    /**
     * Gets all non-flat positions.
     */
    public Collection<Position> getActivePositions() {
        return positions.values().stream()
                .filter(p -> !p.isFlat())
                .toList();
    }

    /**
     * Gets total realized P&L across all positions (in raw scaled units - use with caution).
     * For risk checks, use getTotalRealizedPnlDollars() instead.
     */
    public long getTotalRealizedPnl() {
        // Recalculate for accuracy
        long total = 0;
        for (Position position : positions.values()) {
            total += position.getRealizedPnl();
        }
        return total;
    }

    /**
     * Gets total unrealized P&L across all positions (in raw scaled units - use with caution).
     * For risk checks, use getTotalUnrealizedPnlDollars() instead.
     */
    public long getTotalUnrealizedPnl() {
        long total = 0;
        for (Position position : positions.values()) {
            total += position.getUnrealizedPnl();
        }
        return total;
    }

    /**
     * Gets total P&L (realized + unrealized) in raw scaled units.
     * For risk checks, use getTotalPnlDollars() instead.
     */
    public long getTotalPnl() {
        return getTotalRealizedPnl() + getTotalUnrealizedPnl();
    }

    /**
     * Gets total realized P&L across all positions converted to cents (scale 100).
     * Properly handles positions with different price scales.
     */
    public long getTotalRealizedPnlCents() {
        long totalCents = 0;
        for (Position position : positions.values()) {
            // Convert from position's scale to cents (scale 100)
            // P&L / priceScale * 100 = P&L * 100 / priceScale
            totalCents += position.getRealizedPnl() * 100 / position.getPriceScale();
        }
        return totalCents;
    }

    /**
     * Gets total unrealized P&L across all positions converted to cents (scale 100).
     * Properly handles positions with different price scales.
     */
    public long getTotalUnrealizedPnlCents() {
        long totalCents = 0;
        for (Position position : positions.values()) {
            // Convert from position's scale to cents (scale 100)
            totalCents += position.getUnrealizedPnl() * 100 / position.getPriceScale();
        }
        return totalCents;
    }

    /**
     * Gets total P&L (realized + unrealized) converted to cents (scale 100).
     * Use this for risk limit comparisons where limits are in cents.
     */
    public long getTotalPnlCents() {
        return getTotalRealizedPnlCents() + getTotalUnrealizedPnlCents();
    }

    /**
     * Gets net exposure (long exposure minus short exposure).
     */
    public long getNetExposure() {
        long longExposure = 0;
        long shortExposure = 0;

        for (Position position : positions.values()) {
            if (position.isLong()) {
                longExposure += position.getMarketValue();
            } else if (position.isShort()) {
                shortExposure += position.getMarketValue();
            }
        }

        return longExposure - shortExposure;
    }

    /**
     * Gets gross exposure (long + short exposure separately).
     */
    public GrossExposure getGrossExposure() {
        long longExposure = 0;
        long shortExposure = 0;

        for (Position position : positions.values()) {
            if (position.isLong()) {
                longExposure += position.getMarketValue();
            } else if (position.isShort()) {
                shortExposure += Math.abs(position.getMarketValue());
            }
        }

        return new GrossExposure(longExposure, shortExposure);
    }

    /**
     * Registers a listener for position updates.
     */
    public void addPositionListener(Consumer<Position> listener) {
        positionListeners.add(listener);
    }

    /**
     * Removes a position listener.
     */
    public void removePositionListener(Consumer<Position> listener) {
        positionListeners.remove(listener);
    }

    private void notifyListeners(Position position) {
        for (Consumer<Position> listener : positionListeners) {
            try {
                listener.accept(position);
            } catch (Exception e) {
                log.error("Error in position listener", e);
            }
        }
    }

    /**
     * Restores a position from persisted snapshot data.
     * Used at startup to rebuild in-memory position state.
     */
    public void restorePosition(Symbol symbol, long quantity, long avgEntryPrice,
                                long totalCost, long realizedPnl, long marketValue,
                                long currentPrice, int priceScale, long openedAt) {
        Position position = getOrCreatePosition(symbol);
        position.setQuantity(quantity);
        position.setAverageEntryPrice(avgEntryPrice);
        position.setTotalCost(totalCost);
        position.setRealizedPnl(realizedPnl);
        position.setMarketValue(marketValue);
        position.setCurrentPrice(currentPrice);
        if (priceScale > 0) {
            position.setPriceScale(priceScale);
        }
        position.setOpenedAt(openedAt);
        if (currentPrice > 0) {
            position.updateMarketValue(currentPrice);
        }
        log.info("Restored position: {} qty={} avgEntry={}", symbol, quantity, avgEntryPrice);
    }

    /**
     * Clears all positions (for testing/reset).
     */
    public void clear() {
        positions.clear();
        totalRealizedPnl = 0;
        totalUnrealizedPnl = 0;
    }

    /**
     * Gets a snapshot of all positions.
     */
    public PositionSnapshot getSnapshot() {
        return new PositionSnapshot(
                positions.size(),
                getActivePositions().size(),
                getTotalRealizedPnl(),
                getTotalUnrealizedPnl(),
                getNetExposure(),
                getGrossExposure()
        );
    }

    public record GrossExposure(long longExposure, long shortExposure) {
        public long total() {
            return longExposure + shortExposure;
        }
    }

    public record PositionSnapshot(
            int totalPositions,
            int activePositions,
            long realizedPnl,
            long unrealizedPnl,
            long netExposure,
            GrossExposure grossExposure
    ) {
        public long totalPnl() {
            return realizedPnl + unrealizedPnl;
        }
    }
}
