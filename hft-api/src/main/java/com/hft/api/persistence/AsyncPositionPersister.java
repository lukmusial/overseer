package com.hft.api.persistence;

import com.hft.core.model.Position;
import com.hft.persistence.PositionSnapshotStore;
import com.hft.persistence.PositionSnapshotStore.PositionSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lifts Chronicle position-snapshot writes off the Disruptor consumer thread.
 *
 * <p>The Disruptor consumer used to call
 * {@code persistenceManager.getPositionStore().saveSnapshot(position, nanoTime)}
 * inline from a position listener. Chronicle writes are usually microsecond-range
 * but can spike into milliseconds on page faults or log rotation, and that spike
 * lands on the same thread that must process the next market tick.
 *
 * <p>This class breaks the coupling: the caller thread snapshots the position's
 * scalar fields into an immutable {@link PositionSnapshot} record (a handful of
 * getter reads, cheap) and offers it to a bounded queue. A single dedicated
 * daemon thread drains the queue and writes to Chronicle.
 *
 * <p>Overflow behaviour: the queue is bounded. If it fills, new snapshots are
 * <em>dropped</em> and a counter ticks up. This is intentional — blocking the
 * Disruptor consumer on a full persistence queue would defeat the whole point.
 * Position snapshots are idempotent and redundantly overwrite each other, so a
 * dropped snapshot means a gap in the historical record, not corruption.
 */
public final class AsyncPositionPersister implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncPositionPersister.class);

    private final PositionSnapshotStore store;
    private final BlockingQueue<PositionSnapshot> queue;
    private final Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final LongAdder submitted = new LongAdder();
    private final LongAdder written = new LongAdder();
    private final LongAdder droppedOnOverflow = new LongAdder();

    public AsyncPositionPersister(PositionSnapshotStore store) {
        this(store, 65_536);
    }

    public AsyncPositionPersister(PositionSnapshotStore store, int queueCapacity) {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be > 0");
        }
        this.store = store;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.worker = new Thread(this::drainLoop, "async-position-persister");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /**
     * Caller-thread hot path. Snapshots the position's current fields into an
     * immutable record and offers it to the queue. Non-blocking: if the queue is
     * full the snapshot is dropped and the overflow counter increments.
     */
    public void submit(Position position) {
        if (!running.get()) {
            return;
        }
        PositionSnapshot snapshot = PositionSnapshot.from(position, System.nanoTime());
        submitted.increment();
        if (!queue.offer(snapshot)) {
            droppedOnOverflow.increment();
        }
    }

    private void drainLoop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                PositionSnapshot snapshot = queue.poll(100, TimeUnit.MILLISECONDS);
                if (snapshot == null) continue;
                writeSnapshot(snapshot);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                log.error("Unexpected error in async position persister", t);
            }
        }
    }

    private void writeSnapshot(PositionSnapshot snapshot) {
        // Rebuild a Position on this (off-hot-path) thread so we can call the
        // existing store interface. Allocation here is fine — we're deliberately
        // off the Disruptor consumer's critical path.
        Position p = new Position(snapshot.symbol());
        p.setQuantity(snapshot.quantity());
        p.setAverageEntryPrice(snapshot.averageEntryPrice());
        p.setTotalCost(snapshot.totalCost());
        p.setRealizedPnl(snapshot.realizedPnl());
        p.setUnrealizedPnl(snapshot.unrealizedPnl());
        p.setMarketValue(snapshot.marketValue());
        p.setCurrentPrice(snapshot.currentPrice());
        p.setPriceScale(snapshot.priceScale());
        p.setQuantityScale(snapshot.quantityScale());
        p.setOpenedAt(snapshot.openedAt());
        try {
            store.saveSnapshot(p, snapshot.timestampNanos());
            written.increment();
        } catch (Exception e) {
            log.error("Failed to persist position snapshot for {}: {}",
                    snapshot.symbol(), e.getMessage());
        }
    }

    public long getSubmittedCount() {
        return submitted.sum();
    }

    public long getWrittenCount() {
        return written.sum();
    }

    public long getDroppedCount() {
        return droppedOnOverflow.sum();
    }

    public int getQueueDepth() {
        return queue.size();
    }

    /**
     * Stops accepting submissions, drains the queue, and joins the worker thread.
     * Blocks up to a few seconds — appropriate for orderly shutdown.
     */
    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            try {
                worker.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            long dropped = droppedOnOverflow.sum();
            if (dropped > 0) {
                log.warn("AsyncPositionPersister closed with {} dropped snapshots (overflow)", dropped);
            }
            log.info("AsyncPositionPersister closed: {} submitted, {} written, {} dropped",
                    submitted.sum(), written.sum(), dropped);
        }
    }
}
