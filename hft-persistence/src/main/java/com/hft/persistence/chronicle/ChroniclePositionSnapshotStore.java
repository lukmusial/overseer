package com.hft.persistence.chronicle;

import com.hft.core.model.Position;
import com.hft.core.model.Symbol;
import com.hft.persistence.PositionSnapshotStore;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Chronicle Queue based position snapshot store.
 *
 * Maintains in-memory indices for fast lookups while
 * persisting all position snapshots to Chronicle Queue.
 */
public class ChroniclePositionSnapshotStore implements PositionSnapshotStore {
    private static final Logger log = LoggerFactory.getLogger(ChroniclePositionSnapshotStore.class);

    private final ChronicleQueue queue;
    private final ExcerptAppender appender;

    // In-memory indices
    private final Map<Symbol, PositionSnapshot> latestBySymbol = new ConcurrentHashMap<>();
    private final Map<Symbol, List<PositionSnapshot>> snapshotsBySymbol = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Symbol, PositionSnapshot>> eodSnapshots = new ConcurrentHashMap<>();

    private final int maxSnapshotsPerSymbol;

    public ChroniclePositionSnapshotStore(Path basePath) {
        this(basePath, 1000);
    }

    public ChroniclePositionSnapshotStore(Path basePath, int maxSnapshotsPerSymbol) {
        this.maxSnapshotsPerSymbol = maxSnapshotsPerSymbol;

        this.queue = ChronicleQueue.singleBuilder(basePath.resolve("positions"))
                .build();
        this.appender = queue.createAppender();

        rebuildIndex();

        log.info("Chronicle position snapshot store initialized at {} with {} symbols",
                basePath, latestBySymbol.size());
    }

    private void rebuildIndex() {
        try (ExcerptTailer tailer = queue.createTailer()) {
            PositionSnapshotWire wire = new PositionSnapshotWire();

            while (tailer.readDocument(w -> w.read("position").marshallable(wire))) {
                PositionSnapshot snapshot = wire.toSnapshot();
                Symbol symbol = snapshot.symbol();

                latestBySymbol.put(symbol, snapshot);

                snapshotsBySymbol.compute(symbol, (s, list) -> {
                    if (list == null) {
                        list = new ArrayList<>();
                    }
                    list.add(snapshot);
                    while (list.size() > maxSnapshotsPerSymbol) {
                        list.remove(0);
                    }
                    return list;
                });
            }
        }
    }

    @Override
    public void saveSnapshot(Position position, long timestampNanos) {
        PositionSnapshotWire wire = PositionSnapshotWire.from(position, timestampNanos);

        appender.writeDocument(w -> w.write("position").marshallable(wire));

        PositionSnapshot snapshot = wire.toSnapshot();
        Symbol symbol = position.getSymbol();

        latestBySymbol.put(symbol, snapshot);

        snapshotsBySymbol.compute(symbol, (s, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(snapshot);
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
        return Optional.ofNullable(latestBySymbol.get(symbol));
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
        return new HashMap<>(latestBySymbol);
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
        latestBySymbol.clear();
        snapshotsBySymbol.clear();
        eodSnapshots.clear();
    }

    @Override
    public void close() {
        log.info("Closing Chronicle position snapshot store");
        queue.close();
    }
}
