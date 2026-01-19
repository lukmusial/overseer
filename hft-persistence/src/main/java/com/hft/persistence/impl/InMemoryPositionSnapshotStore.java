package com.hft.persistence.impl;

import com.hft.core.model.Position;
import com.hft.core.model.Symbol;
import com.hft.persistence.PositionSnapshotStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of PositionSnapshotStore.
 */
public class InMemoryPositionSnapshotStore implements PositionSnapshotStore {

    private final Map<Symbol, List<PositionSnapshot>> snapshotsBySymbol = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Symbol, PositionSnapshot>> eodSnapshots = new ConcurrentHashMap<>();
    private final int maxSnapshotsPerSymbol;

    public InMemoryPositionSnapshotStore() {
        this(1000);
    }

    public InMemoryPositionSnapshotStore(int maxSnapshotsPerSymbol) {
        this.maxSnapshotsPerSymbol = maxSnapshotsPerSymbol;
    }

    @Override
    public void saveSnapshot(Position position, long timestampNanos) {
        PositionSnapshot snapshot = PositionSnapshot.from(position, timestampNanos);
        snapshotsBySymbol.compute(position.getSymbol(), (symbol, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(snapshot);
            // Keep only recent snapshots
            while (list.size() > maxSnapshotsPerSymbol) {
                list.remove(0);
            }
            return list;
        });
    }

    @Override
    public void saveAllSnapshots(Map<Symbol, Position> positions, long timestampNanos) {
        for (Map.Entry<Symbol, Position> entry : positions.entrySet()) {
            saveSnapshot(entry.getValue(), timestampNanos);
        }
    }

    @Override
    public Optional<PositionSnapshot> getLatestSnapshot(Symbol symbol) {
        List<PositionSnapshot> snapshots = snapshotsBySymbol.get(symbol);
        if (snapshots == null || snapshots.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(snapshots.get(snapshots.size() - 1));
    }

    @Override
    public List<PositionSnapshot> getSnapshots(Symbol symbol, long startNanos, long endNanos) {
        List<PositionSnapshot> snapshots = snapshotsBySymbol.get(symbol);
        if (snapshots == null) {
            return Collections.emptyList();
        }
        return snapshots.stream()
                .filter(s -> s.timestampNanos() >= startNanos && s.timestampNanos() <= endNanos)
                .collect(Collectors.toList());
    }

    @Override
    public Map<Symbol, PositionSnapshot> getAllLatestSnapshots() {
        Map<Symbol, PositionSnapshot> result = new HashMap<>();
        for (Map.Entry<Symbol, List<PositionSnapshot>> entry : snapshotsBySymbol.entrySet()) {
            List<PositionSnapshot> list = entry.getValue();
            if (!list.isEmpty()) {
                result.put(entry.getKey(), list.get(list.size() - 1));
            }
        }
        return result;
    }

    @Override
    public Map<Symbol, PositionSnapshot> getEndOfDayPositions(int dateYYYYMMDD) {
        return eodSnapshots.getOrDefault(dateYYYYMMDD, Collections.emptyMap());
    }

    @Override
    public void saveEndOfDayPositions(Map<Symbol, Position> positions, int dateYYYYMMDD) {
        long timestamp = System.nanoTime();
        Map<Symbol, PositionSnapshot> snapshots = new HashMap<>();
        for (Map.Entry<Symbol, Position> entry : positions.entrySet()) {
            snapshots.put(entry.getKey(), PositionSnapshot.from(entry.getValue(), timestamp));
        }
        eodSnapshots.put(dateYYYYMMDD, snapshots);
    }

    @Override
    public void clear() {
        snapshotsBySymbol.clear();
        eodSnapshots.clear();
    }

    @Override
    public void close() {
        // No-op for in-memory
    }
}
