package com.hft.persistence.chronicle;

import com.hft.core.model.Exchange;
import com.hft.core.model.Position;
import com.hft.core.model.Symbol;
import com.hft.persistence.PositionSnapshotStore.PositionSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChroniclePositionSnapshotStoreTest {

    @TempDir
    Path tempDir;

    private ChroniclePositionSnapshotStore store;
    private Symbol aaplSymbol;
    private Symbol googSymbol;

    @BeforeEach
    void setUp() {
        store = new ChroniclePositionSnapshotStore(tempDir);
        aaplSymbol = new Symbol("AAPL", Exchange.ALPACA);
        googSymbol = new Symbol("GOOG", Exchange.ALPACA);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void shouldSaveAndRetrieveSnapshot() {
        Position position = createPosition(aaplSymbol, 100, 15000, 500);

        store.saveSnapshot(position, 1_000_000L);

        Optional<PositionSnapshot> snapshot = store.getLatestSnapshot(aaplSymbol);
        assertTrue(snapshot.isPresent());
        assertEquals(aaplSymbol, snapshot.get().symbol());
        assertEquals(100, snapshot.get().quantity());
        assertEquals(15000, snapshot.get().averageEntryPrice());
        assertEquals(500, snapshot.get().realizedPnl());
        assertEquals(1_000_000L, snapshot.get().timestampNanos());
    }

    @Test
    void shouldReturnEmptyForUnknownSymbol() {
        Optional<PositionSnapshot> snapshot = store.getLatestSnapshot(aaplSymbol);
        assertFalse(snapshot.isPresent());
    }

    @Test
    void shouldReturnLatestSnapshotWhenMultipleExist() {
        Position position1 = createPosition(aaplSymbol, 100, 15000, 0);
        Position position2 = createPosition(aaplSymbol, 200, 15100, 100);

        store.saveSnapshot(position1, 1_000_000L);
        store.saveSnapshot(position2, 2_000_000L);

        Optional<PositionSnapshot> snapshot = store.getLatestSnapshot(aaplSymbol);
        assertTrue(snapshot.isPresent());
        assertEquals(200, snapshot.get().quantity());
        assertEquals(2_000_000L, snapshot.get().timestampNanos());
    }

    @Test
    void shouldGetAllLatestSnapshots() {
        Position aapl = createPosition(aaplSymbol, 100, 15000, 0);
        Position goog = createPosition(googSymbol, 50, 280000, 200);

        store.saveSnapshot(aapl, 1_000_000L);
        store.saveSnapshot(goog, 2_000_000L);

        Map<Symbol, PositionSnapshot> latest = store.getAllLatestSnapshots();
        assertEquals(2, latest.size());
        assertEquals(100, latest.get(aaplSymbol).quantity());
        assertEquals(50, latest.get(googSymbol).quantity());
    }

    @Test
    void shouldGetSnapshotsInTimeRange() {
        Position position = createPosition(aaplSymbol, 100, 15000, 0);

        store.saveSnapshot(position, 1_000_000L);
        store.saveSnapshot(position, 2_000_000L);
        store.saveSnapshot(position, 3_000_000L);
        store.saveSnapshot(position, 4_000_000L);

        List<PositionSnapshot> snapshots = store.getSnapshots(aaplSymbol, 2_000_000L, 3_000_000L);
        assertEquals(2, snapshots.size());
        assertEquals(2_000_000L, snapshots.get(0).timestampNanos());
        assertEquals(3_000_000L, snapshots.get(1).timestampNanos());
    }

    @Test
    void shouldReturnEmptyListForTimeRangeWithNoData() {
        List<PositionSnapshot> snapshots = store.getSnapshots(aaplSymbol, 1_000_000L, 2_000_000L);
        assertTrue(snapshots.isEmpty());
    }

    @Test
    void shouldSaveAndRetrieveEndOfDayPositions() {
        Position aapl = createPosition(aaplSymbol, 100, 15000, 500);
        Position goog = createPosition(googSymbol, 50, 280000, -200);

        Map<Symbol, Position> positions = Map.of(aaplSymbol, aapl, googSymbol, goog);
        store.saveEndOfDayPositions(positions, 20260206);

        Map<Symbol, PositionSnapshot> eod = store.getEndOfDayPositions(20260206);
        assertEquals(2, eod.size());
        assertEquals(100, eod.get(aaplSymbol).quantity());
        assertEquals(50, eod.get(googSymbol).quantity());
    }

    @Test
    void shouldReturnEmptyMapForUnknownEodDate() {
        Map<Symbol, PositionSnapshot> eod = store.getEndOfDayPositions(20260101);
        assertTrue(eod.isEmpty());
    }

    @Test
    void shouldPersistAndRebuildFromDisk() {
        Position aapl = createPosition(aaplSymbol, 100, 15000, 500);
        Position goog = createPosition(googSymbol, -50, 280000, -200);

        store.saveSnapshot(aapl, 1_000_000L);
        store.saveSnapshot(goog, 2_000_000L);

        // Update AAPL position
        Position aaplUpdated = createPosition(aaplSymbol, 150, 15050, 700);
        store.saveSnapshot(aaplUpdated, 3_000_000L);

        store.close();

        // Reopen store — should rebuild from Chronicle Queue
        ChroniclePositionSnapshotStore store2 = new ChroniclePositionSnapshotStore(tempDir);

        Map<Symbol, PositionSnapshot> latest = store2.getAllLatestSnapshots();
        assertEquals(2, latest.size());

        // AAPL should reflect the latest snapshot
        PositionSnapshot aaplSnapshot = latest.get(aaplSymbol);
        assertNotNull(aaplSnapshot);
        assertEquals(150, aaplSnapshot.quantity());
        assertEquals(15050, aaplSnapshot.averageEntryPrice());
        assertEquals(700, aaplSnapshot.realizedPnl());
        assertEquals(3_000_000L, aaplSnapshot.timestampNanos());

        // GOOG should be persisted too
        PositionSnapshot googSnapshot = latest.get(googSymbol);
        assertNotNull(googSnapshot);
        assertEquals(-50, googSnapshot.quantity());

        // Time range query should work after rebuild
        List<PositionSnapshot> aaplHistory = store2.getSnapshots(aaplSymbol, 0, Long.MAX_VALUE);
        assertEquals(2, aaplHistory.size());

        store2.close();
    }

    @Test
    void shouldSaveAllSnapshots() {
        Position aapl = createPosition(aaplSymbol, 100, 15000, 0);
        Position goog = createPosition(googSymbol, 50, 280000, 0);

        Map<Symbol, Position> positions = Map.of(aaplSymbol, aapl, googSymbol, goog);
        store.saveAllSnapshots(positions, 1_000_000L);

        assertEquals(2, store.getAllLatestSnapshots().size());
    }

    @Test
    void shouldClearAllData() {
        Position aapl = createPosition(aaplSymbol, 100, 15000, 0);
        store.saveSnapshot(aapl, 1_000_000L);

        store.clear();

        assertTrue(store.getAllLatestSnapshots().isEmpty());
        assertFalse(store.getLatestSnapshot(aaplSymbol).isPresent());
    }

    private Position createPosition(Symbol symbol, long quantity, long avgEntryPrice, long realizedPnl) {
        Position position = new Position(symbol);
        position.setQuantity(quantity);
        position.setAverageEntryPrice(avgEntryPrice);
        position.setRealizedPnl(realizedPnl);
        position.setCurrentPrice(avgEntryPrice);
        position.setMarketValue(avgEntryPrice * Math.abs(quantity) / position.getPriceScale());
        position.setTotalCost(avgEntryPrice * Math.abs(quantity) / position.getPriceScale());
        return position;
    }
}
