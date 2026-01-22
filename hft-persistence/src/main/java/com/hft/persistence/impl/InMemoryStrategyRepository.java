package com.hft.persistence.impl;

import com.hft.persistence.StrategyRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory strategy repository for testing.
 */
public class InMemoryStrategyRepository implements StrategyRepository {

    private final Map<String, StrategyDefinition> strategies = new ConcurrentHashMap<>();

    @Override
    public void save(StrategyDefinition strategy) {
        strategies.put(strategy.id(), strategy);
    }

    @Override
    public Optional<StrategyDefinition> findById(String id) {
        return Optional.ofNullable(strategies.get(id));
    }

    @Override
    public List<StrategyDefinition> findAll() {
        return new ArrayList<>(strategies.values());
    }

    @Override
    public void delete(String id) {
        strategies.remove(id);
    }

    @Override
    public void flush() {
        // No-op
    }

    @Override
    public void close() {
        // No-op
    }
}
