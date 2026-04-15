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

    // Aggregate P&L tracking (raw scaled units)
    private volatile long totalRealizedPnl;
    private volatile long totalUnrealizedPnl;

    // Incrementally maintained P&L in cents (scale 100) for O(1) risk checks
    private volatile long cachedRealizedPnlCents;
    private volatile long cachedUnrealizedPnlCents;

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

        // Set quantity scale from the trade's symbol exchange
        if (trade.getSymbol() != null) {
            position.setQuantityScale(trade.getSymbol().getExchange().getQuantityScale());
        }

        long previousRealizedPnl = position.getRealizedPnl();
        position.applyTrade(trade);
        long newRealizedPnl = position.getRealizedPnl();

        // Update aggregate P&L (raw units)
        long realizedDelta = newRealizedPnl - previousRealizedPnl;
        totalRealizedPnl += realizedDelta;

        // Update incremental cents cache: convert delta from position's scale to cents
        int priceScale = position.getPriceScale();
        cachedRealizedPnlCents += realizedDelta * 100 / priceScale;

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

            // Update aggregate unrealized P&L (raw units)
            long unrealizedDelta = newUnrealized - previousUnrealized;
            totalUnrealizedPnl += unrealizedDelta;

            // Update incremental cents cache
            int priceScale = position.getPriceScale();
            cachedUnrealizedPnlCents += unrealizedDelta * 100 / priceScale;

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
     * O(1) — uses incrementally maintained cache updated in applyTrade().
     */
    public long getTotalRealizedPnlCents() {
        return cachedRealizedPnlCents;
    }

    /**
     * Gets total unrealized P&L across all positions converted to cents (scale 100).
     * O(1) — uses incrementally maintained cache updated in updateMarketValue().
     */
    public long getTotalUnrealizedPnlCents() {
        return cachedUnrealizedPnlCents;
    }

    /**
     * Gets total P&L (realized + unrealized) converted to cents (scale 100).
     * O(1) — uses incrementally maintained caches for hot-path risk checks.
     */
    public long getTotalPnlCents() {
        return cachedRealizedPnlCents + cachedUnrealizedPnlCents;
    }

    /**
     * Recalculates the P&L cents caches from scratch by iterating all positions.
     * Use after bulk position restoration or to correct any accumulated rounding drift.
     */
    public void recalculatePnlCentsCache() {
        long realizedCents = 0;
        long unrealizedCents = 0;
        for (Position position : positions.values()) {
            realizedCents += position.getRealizedPnl() * 100 / position.getPriceScale();
            unrealizedCents += position.getUnrealizedPnl() * 100 / position.getPriceScale();
        }
        cachedRealizedPnlCents = realizedCents;
        cachedUnrealizedPnlCents = unrealizedCents;
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
        restorePosition(symbol, quantity, avgEntryPrice, totalCost, realizedPnl,
                marketValue, currentPrice, priceScale, openedAt, 1);
    }

    /**
     * Restores a position from persisted snapshot data with quantity scale.
     */
    public void restorePosition(Symbol symbol, long quantity, long avgEntryPrice,
                                long totalCost, long realizedPnl, long marketValue,
                                long currentPrice, int priceScale, long openedAt,
                                int quantityScale) {
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
        if (quantityScale > 0) {
            position.setQuantityScale(quantityScale);
        }
        position.setOpenedAt(openedAt);
        if (currentPrice > 0) {
            position.updateMarketValue(currentPrice);
        }
        // Recalculate cents caches after each restoration to stay consistent
        recalculatePnlCentsCache();
        log.info("Restored position: {} qty={} avgEntry={}", symbol, quantity, avgEntryPrice);
    }

    /**
     * Clears all positions (for testing/reset).
     */
    public void clear() {
        positions.clear();
        totalRealizedPnl = 0;
        totalUnrealizedPnl = 0;
        cachedRealizedPnlCents = 0;
        cachedUnrealizedPnlCents = 0;
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
