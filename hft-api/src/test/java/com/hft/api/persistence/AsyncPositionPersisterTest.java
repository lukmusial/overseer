package com.hft.api.persistence;

import com.hft.core.model.Exchange;
import com.hft.core.model.Position;
import com.hft.core.model.Symbol;
import com.hft.persistence.PositionSnapshotStore;
import com.hft.persistence.PositionSnapshotStore.PositionSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AsyncPositionPersisterTest {

    private RecordingStore store;
    private AsyncPositionPersister persister;
    private Symbol aapl;
    private Symbol msft;

    @BeforeEach
    void setUp() {
        store = new RecordingStore();
        aapl = new Symbol("AAPL", Exchange.ALPACA);
        msft = new Symbol("MSFT", Exchange.ALPACA);
    }

    @AfterEach
    void tearDown() {
        if (persister != null) persister.close();
    }

    @Test
    void submitDrainsToStoreAsynchronously() throws InterruptedException {
        persister = new AsyncPositionPersister(store, 1024);

        Position p = new Position(aapl);
        p.setQuantity(100);
        p.setAverageEntryPrice(150_00L);
        p.setMarketValue(150_00L * 100);

        persister.submit(p);

        assertTrue(store.awaitWrites(1, 2, TimeUnit.SECONDS),
                "snapshot must reach the store within 2s");
        assertEquals(1, persister.getWrittenCount());
        assertEquals(0, persister.getDroppedCount());
    }

    @Test
    void submitPreservesFieldsOnAsyncPath() throws InterruptedException {
        persister = new AsyncPositionPersister(store, 1024);

        Position p = new Position(aapl);
        p.setQuantity(250);
        p.setAverageEntryPrice(200_00L);
        p.setRealizedPnl(1_500L);
        p.setUnrealizedPnl(500L);
        p.setCurrentPrice(201_00L);
        p.setMarketValue(201_00L * 250);
        p.setTotalCost(200_00L * 250);
        p.setPriceScale(100);
        p.setQuantityScale(1);
        p.setOpenedAt(42_000_000_000L);

        persister.submit(p);
        assertTrue(store.awaitWrites(1, 2, TimeUnit.SECONDS));

        Optional<PositionSnapshot> saved = store.getLatestSnapshot(aapl);
        assertTrue(saved.isPresent());
        PositionSnapshot snap = saved.get();
        assertEquals(aapl, snap.symbol());
        assertEquals(250, snap.quantity());
        assertEquals(200_00L, snap.averageEntryPrice());
        assertEquals(1_500L, snap.realizedPnl());
        assertEquals(500L, snap.unrealizedPnl());
        assertEquals(201_00L, snap.currentPrice());
        assertEquals(201_00L * 250, snap.marketValue());
        assertEquals(200_00L * 250, snap.totalCost());
        assertEquals(42_000_000_000L, snap.openedAt());
    }

    @Test
    void mutatingPositionAfterSubmitDoesNotCorruptSnapshot() throws InterruptedException {
        // Block the drain thread so the snapshot sits in the queue while we mutate.
        CountDownLatch release = new CountDownLatch(1);
        store.setBeforeWriteHook(release::await);

        persister = new AsyncPositionPersister(store, 1024);

        Position p = new Position(aapl);
        p.setQuantity(100);
        p.setAverageEntryPrice(150_00L);

        persister.submit(p);

        // Mutate the position after submit — this simulates the Disruptor consumer
        // updating the next tick while the persister is still busy with the previous.
        p.setQuantity(999);
        p.setAverageEntryPrice(999_99L);

        release.countDown();
        assertTrue(store.awaitWrites(1, 2, TimeUnit.SECONDS));

        PositionSnapshot saved = store.getLatestSnapshot(aapl).orElseThrow();
        assertEquals(100, saved.quantity(), "snapshot must reflect the pre-mutation values");
        assertEquals(150_00L, saved.averageEntryPrice());
    }

    @Test
    void overflowDropsNewestAndIncrementsCounter() throws InterruptedException {
        // Block the drain indefinitely to guarantee the queue fills.
        CountDownLatch release = new CountDownLatch(1);
        store.setBeforeWriteHook(release::await);

        persister = new AsyncPositionPersister(store, 4);

        Position p = new Position(aapl);
        p.setQuantity(1);

        // First submit may start being drained (blocked at the hook), leaving 3 slots.
        // Submitting 20 should fill the queue and drop the remainder.
        for (int i = 0; i < 20; i++) {
            persister.submit(p);
        }

        assertEquals(20, persister.getSubmittedCount());
        assertTrue(persister.getDroppedCount() > 0,
                "expected drops once the queue filled; got " + persister.getDroppedCount());

        release.countDown();
    }

    @Test
    void closeDrainsPendingSnapshotsBeforeReturning() throws InterruptedException {
        persister = new AsyncPositionPersister(store, 1024);

        Position p = new Position(msft);
        p.setQuantity(50);
        for (int i = 0; i < 200; i++) {
            persister.submit(p);
        }

        persister.close();

        assertEquals(200, persister.getSubmittedCount());
        assertEquals(200, persister.getWrittenCount(),
                "close() must wait for pending snapshots to be written");
    }

    @Test
    void zeroQueueCapacityRejectedByConstructor() {
        assertThrows(IllegalArgumentException.class,
                () -> new AsyncPositionPersister(store, 0));
    }

    /**
     * Minimal PositionSnapshotStore test double: collects writes, supports a
     * pre-write hook for forcing overflow / mutation races, thread-safe latch
     * for awaiting write completion.
     */
    static final class RecordingStore implements PositionSnapshotStore {
        private final List<PositionSnapshot> writes = new ArrayList<>();
        private final AtomicInteger writeCount = new AtomicInteger();
        private volatile BeforeWrite beforeWriteHook = () -> {};

        void setBeforeWriteHook(BeforeWrite hook) {
            this.beforeWriteHook = hook;
        }

        synchronized boolean awaitWrites(int n, long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (writeCount.get() < n) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                wait(TimeUnit.NANOSECONDS.toMillis(Math.min(remaining, 10_000_000L)) + 1);
            }
            return true;
        }

        @Override
        public void saveSnapshot(Position position, long timestampNanos) {
            try {
                beforeWriteHook.before();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            PositionSnapshot snap = PositionSnapshot.from(position, timestampNanos);
            synchronized (this) {
                writes.add(snap);
                writeCount.incrementAndGet();
                notifyAll();
            }
        }

        @Override
        public void saveAllSnapshots(Map<Symbol, Position> positions, long timestampNanos) {
            positions.values().forEach(p -> saveSnapshot(p, timestampNanos));
        }

        @Override
        public synchronized Optional<PositionSnapshot> getLatestSnapshot(Symbol symbol) {
            for (int i = writes.size() - 1; i >= 0; i--) {
                if (writes.get(i).symbol().equals(symbol)) {
                    return Optional.of(writes.get(i));
                }
            }
            return Optional.empty();
        }

        @Override public List<PositionSnapshot> getSnapshots(Symbol s, long a, long b) { return List.of(); }
        @Override public Map<Symbol, PositionSnapshot> getAllLatestSnapshots() { return Map.of(); }
        @Override public Map<Symbol, PositionSnapshot> getEndOfDayPositions(int d) { return Map.of(); }
        @Override public void saveEndOfDayPositions(Map<Symbol, Position> p, int d) {}
        @Override public synchronized void clear() { writes.clear(); writeCount.set(0); }
        @Override public void close() {}

        @FunctionalInterface
        interface BeforeWrite { void before() throws InterruptedException; }
    }
}
