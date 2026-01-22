package com.hft.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for persisting trading strategy definitions.
 * Strategies are stored and restored across application restarts.
 */
public interface StrategyRepository {

    /**
     * Saves a strategy definition.
     * If a strategy with the same ID exists, it will be updated.
     */
    void save(StrategyDefinition strategy);

    /**
     * Finds a strategy by its ID.
     */
    Optional<StrategyDefinition> findById(String id);

    /**
     * Returns all saved strategies.
     */
    List<StrategyDefinition> findAll();

    /**
     * Deletes a strategy by its ID.
     */
    void delete(String id);

    /**
     * Flushes any pending writes to storage.
     */
    void flush();

    /**
     * Closes the repository and releases resources.
     */
    void close();

    /**
     * Immutable strategy definition for persistence.
     */
    record StrategyDefinition(
            String id,
            String name,
            String type,
            List<String> symbols,
            String exchange,
            Map<String, Object> parameters,
            String state
    ) {}
}
