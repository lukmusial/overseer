package com.hft.engine.service;

import com.hft.core.model.*;
import com.hft.core.util.ListenerSet;
import com.hft.core.util.PaddedVolatileLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages positions across all symbols.
 * Thread-safe position tracking with P&L calculations.
 */
public class PositionManager {
    private static final Logger log = LoggerFactory.getLogger(PositionManager.class);

    private final Map<Symbol, Position> positions;
    private final ListenerSet<Position> positionListeners;
    private final ListenerSet.ExceptionSink logError =
            (listener, cause) -> log.error("Error in position listener", cause);

    // Aggregate P&L tracking (raw scaled units). Padded to their own cache
    // lines so writes from the Disruptor consumer (per-tick) do not invalidate
    // reader caches (UI / risk-checker / Tomcat workers) that hit adjacent
    // counters. See Phase 5 plan for the analysis.
    private final PaddedVolatileLong totalRealizedPnl = new PaddedVolatileLong();
    private final PaddedVolatileLong totalUnrealizedPnl = new PaddedVolatileLong();

    // Incrementally maintained P&L in cents (scale 100) for O(1) risk checks.
    private final PaddedVolatileLong cachedRealizedPnlCents = new PaddedVolatileLong();
    private final PaddedVolatileLong cachedUnrealizedPnlCents = new PaddedVolatileLong();

    public PositionManager() {
        this.positions = new ConcurrentHashMap<>();
        this.positionListeners = new ListenerSet<>();
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
        totalRealizedPnl.addAndGet(realizedDelta);

        // Update incremental cents cache: convert delta from position's scale to cents
        int priceScale = position.getPriceScale();
        cachedRealizedPnlCents.addAndGet(realizedDelta * 100 / priceScale);

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
            totalUnrealizedPnl.addAndGet(unrealizedDelta);

            // Update incremental cents cache
            int priceScale = position.getPriceScale();
            cachedUnrealizedPnlCents.addAndGet(unrealizedDelta * 100 / priceScale);

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
        return cachedRealizedPnlCents.get();
    }

    /**
     * Gets total unrealized P&L across all positions converted to cents (scale 100).
     * O(1) — uses incrementally maintained cache updated in updateMarketValue().
     */
    public long getTotalUnrealizedPnlCents() {
        return cachedUnrealizedPnlCents.get();
    }

    /**
     * Gets total P&L (realized + unrealized) converted to cents (scale 100).
     * O(1) — uses incrementally maintained caches for hot-path risk checks.
     */
    public long getTotalPnlCents() {
        return cachedRealizedPnlCents.get() + cachedUnrealizedPnlCents.get();
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
        cachedRealizedPnlCents.set(realizedCents);
        cachedUnrealizedPnlCents.set(unrealizedCents);
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
        positionListeners.notify(position, logError);
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
     * Reconciles a position's quantity with the actual exchange balance.
     * Adjusts the internal position to match reality without affecting realized P&L.
     */
    public void reconcileQuantity(Symbol symbol, long actualQuantity) {
        // Derive scales from exchange
        int quantityScale = symbol.getExchange().getQuantityScale();
        int priceScale = quantityScale > 1 ? quantityScale : 100; // crypto uses same scale for both

        Position position = positions.get(symbol);
        if (position == null) {
            if (actualQuantity != 0) {
                // Position exists on exchange but not internally — create it
                position = getOrCreatePosition(symbol);
                position.setQuantityScale(quantityScale);
                position.setPriceScale(priceScale);
                position.setQuantity(actualQuantity);
                log.warn("Reconcile: created missing position {} qty={}", symbol, actualQuantity);
                notifyListeners(position);
            }
            return;
        }

        // Ensure scales are correct (may have been created with defaults)
        position.setQuantityScale(quantityScale);
        position.setPriceScale(priceScale);

        long currentQty = position.getQuantity();
        if (currentQty == actualQuantity) {
            return; // Already in sync
        }

        log.warn("Reconcile: {} position drift detected: internal={} exchange={}, adjusting",
                symbol, currentQty, actualQuantity);
        position.setQuantity(actualQuantity);
        if (actualQuantity == 0) {
            position.setAverageEntryPrice(0);
            position.setTotalCost(0);
        }
        if (position.getCurrentPrice() > 0) {
            position.updateMarketValue(position.getCurrentPrice());
        }
        recalculatePnlCentsCache();
        notifyListeners(position);
    }

    /**
     * Clears all positions (for testing/reset).
     */
    public void clear() {
        positions.clear();
        totalRealizedPnl.set(0);
        totalUnrealizedPnl.set(0);
        cachedRealizedPnlCents.set(0);
        cachedUnrealizedPnlCents.set(0);
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
