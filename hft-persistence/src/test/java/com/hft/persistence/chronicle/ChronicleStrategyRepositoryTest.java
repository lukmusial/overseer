package com.hft.persistence.chronicle;

import com.hft.persistence.StrategyRepository;
import com.hft.persistence.StrategyRepository.StrategyDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChronicleStrategyRepositoryTest {

    @TempDir
    Path tempDir;

    private ChronicleStrategyRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ChronicleStrategyRepository(tempDir);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void save_shouldPersistStrategy() {
        StrategyDefinition def = new StrategyDefinition(
                "strat-1", "My Momentum", "momentum",
                List.of("AAPL", "GOOGL"), "ALPACA",
                Map.of("shortPeriod", 10L, "longPeriod", 30L),
                "INITIALIZED"
        );

        repository.save(def);

        Optional<StrategyDefinition> found = repository.findById("strat-1");
        assertTrue(found.isPresent());
        assertEquals("strat-1", found.get().id());
        assertEquals("My Momentum", found.get().name());
        assertEquals("momentum", found.get().type());
        assertEquals(List.of("AAPL", "GOOGL"), found.get().symbols());
    }

    @Test
    void findAll_shouldReturnAllStrategies() {
        repository.save(new StrategyDefinition(
                "strat-1", "Strategy 1", "vwap",
                List.of("AAPL"), "ALPACA", Map.of(), "INITIALIZED"
        ));
        repository.save(new StrategyDefinition(
                "strat-2", "Strategy 2", "twap",
                List.of("GOOGL"), "ALPACA", Map.of(), "INITIALIZED"
        ));

        List<StrategyDefinition> all = repository.findAll();

        assertEquals(2, all.size());
    }

    @Test
    void delete_shouldRemoveStrategy() {
        repository.save(new StrategyDefinition(
                "strat-1", "Strategy 1", "momentum",
                List.of("AAPL"), "ALPACA", Map.of(), "INITIALIZED"
        ));

        repository.delete("strat-1");

        Optional<StrategyDefinition> found = repository.findById("strat-1");
        assertFalse(found.isPresent());
    }

    @Test
    void save_shouldUpdateExistingStrategy() {
        repository.save(new StrategyDefinition(
                "strat-1", "Original Name", "momentum",
                List.of("AAPL"), "ALPACA", Map.of(), "INITIALIZED"
        ));

        repository.save(new StrategyDefinition(
                "strat-1", "Updated Name", "momentum",
                List.of("AAPL", "MSFT"), "ALPACA", Map.of(), "RUNNING"
        ));

        Optional<StrategyDefinition> found = repository.findById("strat-1");
        assertTrue(found.isPresent());
        assertEquals("Updated Name", found.get().name());
        assertEquals(List.of("AAPL", "MSFT"), found.get().symbols());
        assertEquals("RUNNING", found.get().state());
    }

    @Test
    void rebuildIndex_shouldRestoreFromDisk() {
        // Save some strategies
        repository.save(new StrategyDefinition(
                "strat-1", "Strategy 1", "vwap",
                List.of("AAPL"), "ALPACA", Map.of("totalQuantity", 1000L), "INITIALIZED"
        ));
        repository.save(new StrategyDefinition(
                "strat-2", "Strategy 2", "twap",
                List.of("GOOGL"), "BINANCE", Map.of("durationMinutes", 60L), "RUNNING"
        ));
        repository.delete("strat-1");

        // Close and reopen repository
        repository.close();
        repository = new ChronicleStrategyRepository(tempDir);

        // Only strat-2 should exist
        List<StrategyDefinition> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals("strat-2", all.get(0).id());
        assertEquals("Strategy 2", all.get(0).name());
        assertEquals(60L, all.get(0).parameters().get("durationMinutes"));
    }

    @Test
    void rebuildIndex_shouldRestoreNewStrategiesCreatedAfterDeletes() {
        // Save some strategies
        repository.save(new StrategyDefinition(
                "old-1", "Old Strategy 1", "momentum",
                List.of("AAPL"), "ALPACA", Map.of("shortPeriod", 10L), "INITIALIZED"
        ));
        repository.save(new StrategyDefinition(
                "old-2", "Old Strategy 2", "vwap",
                List.of("GOOGL"), "ALPACA", Map.of(), "INITIALIZED"
        ));

        // Delete all old strategies
        repository.delete("old-1");
        repository.delete("old-2");

        // Save new strategies after the deletes
        repository.save(new StrategyDefinition(
                "new-1", "New Strategy 1", "twap",
                List.of("BTCUSDT"), "BINANCE", Map.of("durationMinutes", 30L), "INITIALIZED"
        ));
        repository.save(new StrategyDefinition(
                "new-2", "New Strategy 2", "meanreversion",
                List.of("ETHUSDT"), "BINANCE", Map.of("lookbackPeriod", 20L), "RUNNING"
        ));

        // Close and reopen to force replay from Chronicle Queue
        repository.close();
        repository = new ChronicleStrategyRepository(tempDir);

        // Only new strategies should exist
        List<StrategyDefinition> all = repository.findAll();
        assertEquals(2, all.size());

        Map<String, StrategyDefinition> byId = new java.util.HashMap<>();
        all.forEach(d -> byId.put(d.id(), d));

        assertFalse(byId.containsKey("old-1"), "Deleted strategy old-1 should not reappear");
        assertFalse(byId.containsKey("old-2"), "Deleted strategy old-2 should not reappear");
        assertTrue(byId.containsKey("new-1"), "New strategy new-1 should survive restart");
        assertTrue(byId.containsKey("new-2"), "New strategy new-2 should survive restart");
        assertEquals("New Strategy 1", byId.get("new-1").name());
        assertEquals("twap", byId.get("new-1").type());
        assertEquals("New Strategy 2", byId.get("new-2").name());
        assertEquals("RUNNING", byId.get("new-2").state());
    }

    @Test
    void parametersWithSpecialCharacters_shouldBePreserved() {
        Map<String, Object> params = Map.of(
                "key=with=equals", "value;with;semicolons",
                "normalKey", 42L
        );

        repository.save(new StrategyDefinition(
                "strat-1", "Test Strategy", "momentum",
                List.of("AAPL"), "ALPACA", params, "INITIALIZED"
        ));

        Optional<StrategyDefinition> found = repository.findById("strat-1");
        assertTrue(found.isPresent());
        assertEquals("value;with;semicolons", found.get().parameters().get("key=with=equals"));
        assertEquals(42L, found.get().parameters().get("normalKey"));
    }
}
