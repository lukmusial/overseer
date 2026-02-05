package com.hft.persistence.chronicle;

import com.hft.persistence.StrategyRepository;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chronicle Queue based strategy repository.
 *
 * Maintains an in-memory index for fast lookups while
 * persisting all strategy definitions to Chronicle Queue.
 *
 * Uses ThreadLocal appenders because Chronicle Queue's StoreAppender
 * has thread affinity and cannot be shared across threads.
 */
public class ChronicleStrategyRepository implements StrategyRepository {
    private static final Logger log = LoggerFactory.getLogger(ChronicleStrategyRepository.class);

    private final ChronicleQueue queue;
    private final ThreadLocal<ExcerptAppender> appenderLocal;

    // In-memory index for fast lookups
    private final Map<String, StrategyDefinition> strategiesById = new ConcurrentHashMap<>();

    public ChronicleStrategyRepository(Path basePath) {
        this.queue = ChronicleQueue.singleBuilder(basePath.resolve("strategies"))
                .build();
        this.appenderLocal = ThreadLocal.withInitial(queue::createAppender);

        // Rebuild in-memory index from queue
        rebuildIndex();

        log.info("Chronicle strategy repository initialized at {} with {} strategies",
                basePath, strategiesById.size());
    }

    private void rebuildIndex() {
        int totalRecords = 0;
        int deleteRecords = 0;
        try (ExcerptTailer tailer = queue.createTailer()) {
            while (true) {
                // Create a fresh wire per document to avoid state leakage
                // (e.g. 'deleted' flag persisting across reads)
                StrategyWire wire = new StrategyWire();
                if (!tailer.readDocument(w -> w.read("strategy").marshallable(wire))) {
                    break;
                }
                totalRecords++;
                if (wire.isDeleted()) {
                    deleteRecords++;
                    String removedId = wire.getId();
                    strategiesById.remove(removedId);
                    log.debug("Replay: delete strategy {}", removedId);
                } else {
                    StrategyDefinition def = wire.toDefinition();
                    if (def != null) {
                        strategiesById.put(def.id(), def);
                        log.debug("Replay: save strategy {} ({})", def.id(), def.name());
                    }
                }
            }
        }
        log.info("Replayed {} records ({} deletes), {} strategies active",
                totalRecords, deleteRecords, strategiesById.size());
    }

    @Override
    public void save(StrategyDefinition strategy) {
        StrategyWire wire = StrategyWire.from(strategy);
        appenderLocal.get().writeDocument(w -> w.write("strategy").marshallable(wire));
        strategiesById.put(strategy.id(), strategy);
        log.debug("Saved strategy: {} ({})", strategy.id(), strategy.name());
    }

    @Override
    public Optional<StrategyDefinition> findById(String id) {
        return Optional.ofNullable(strategiesById.get(id));
    }

    @Override
    public List<StrategyDefinition> findAll() {
        return new ArrayList<>(strategiesById.values());
    }

    @Override
    public void delete(String id) {
        StrategyWire wire = StrategyWire.deleted(id);
        appenderLocal.get().writeDocument(w -> w.write("strategy").marshallable(wire));
        strategiesById.remove(id);
        log.info("Deleted strategy: {} (deleted flag: {})", id, wire.isDeleted());
    }

    @Override
    public void flush() {
        // Chronicle Queue writes go directly to memory-mapped file.
        // The OS handles flushing to disk.
    }

    @Override
    public void close() {
        log.info("Closing Chronicle strategy repository ({} strategies)", strategiesById.size());
        queue.close();
    }
}
