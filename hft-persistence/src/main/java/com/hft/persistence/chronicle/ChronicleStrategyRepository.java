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
 */
public class ChronicleStrategyRepository implements StrategyRepository {
    private static final Logger log = LoggerFactory.getLogger(ChronicleStrategyRepository.class);

    private final ChronicleQueue queue;
    private final ExcerptAppender appender;

    // In-memory index for fast lookups
    private final Map<String, StrategyDefinition> strategiesById = new ConcurrentHashMap<>();

    public ChronicleStrategyRepository(Path basePath) {
        this.queue = ChronicleQueue.singleBuilder(basePath.resolve("strategies"))
                .build();
        this.appender = queue.createAppender();

        // Rebuild in-memory index from queue
        rebuildIndex();

        log.info("Chronicle strategy repository initialized at {} with {} strategies",
                basePath, strategiesById.size());
    }

    private void rebuildIndex() {
        try (ExcerptTailer tailer = queue.createTailer()) {
            StrategyWire wire = new StrategyWire();

            while (tailer.readDocument(w -> w.read("strategy").marshallable(wire))) {
                if (wire.isDeleted()) {
                    strategiesById.remove(wire.getId());
                } else {
                    StrategyDefinition def = wire.toDefinition();
                    if (def != null) {
                        strategiesById.put(def.id(), def);
                    }
                }
            }
        }
    }

    @Override
    public void save(StrategyDefinition strategy) {
        StrategyWire wire = StrategyWire.from(strategy);
        appender.writeDocument(w -> w.write("strategy").marshallable(wire));
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
        appender.writeDocument(w -> w.write("strategy").marshallable(wire));
        strategiesById.remove(id);
        log.debug("Deleted strategy: {}", id);
    }

    @Override
    public void flush() {
        // Chronicle auto-flushes
    }

    @Override
    public void close() {
        log.info("Closing Chronicle strategy repository");
        queue.close();
    }
}
