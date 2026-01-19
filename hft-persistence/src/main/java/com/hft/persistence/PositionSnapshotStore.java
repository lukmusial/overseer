package com.hft.persistence;

import com.hft.core.model.Position;
import com.hft.core.model.Symbol;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for position snapshot persistence.
 * Stores point-in-time snapshots of positions.
 */
public interface PositionSnapshotStore {

    /**
     * Saves a position snapshot.
     */
    void saveSnapshot(Position position, long timestampNanos);

    /**
     * Saves snapshots of all positions.
     */
    void saveAllSnapshots(Map<Symbol, Position> positions, long timestampNanos);

    /**
     * Gets the latest snapshot for a symbol.
     */
    Optional<PositionSnapshot> getLatestSnapshot(Symbol symbol);

    /**
     * Gets all snapshots for a symbol within a time range.
     */
    List<PositionSnapshot> getSnapshots(Symbol symbol, long startNanos, long endNanos);

    /**
     * Gets all latest snapshots.
     */
    Map<Symbol, PositionSnapshot> getAllLatestSnapshots();

    /**
     * Gets end-of-day positions for a date.
     */
    Map<Symbol, PositionSnapshot> getEndOfDayPositions(int dateYYYYMMDD);

    /**
     * Saves end-of-day positions.
     */
    void saveEndOfDayPositions(Map<Symbol, Position> positions, int dateYYYYMMDD);

    /**
     * Clears all snapshots.
     */
    void clear();

    /**
     * Closes the store.
     */
    void close();

    /**
     * Immutable position snapshot.
     */
    record PositionSnapshot(
            Symbol symbol,
            long quantity,
            long averageEntryPrice,
            long realizedPnl,
            long unrealizedPnl,
            long marketValue,
            long timestampNanos
    ) {
        public static PositionSnapshot from(Position position, long timestampNanos) {
            return new PositionSnapshot(
                    position.getSymbol(),
                    position.getQuantity(),
                    position.getAverageEntryPrice(),
                    position.getRealizedPnl(),
                    position.getUnrealizedPnl(),
                    position.getMarketValue(),
                    timestampNanos
            );
        }
    }
}
