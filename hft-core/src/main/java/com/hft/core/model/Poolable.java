package com.hft.core.model;

/**
 * Interface for objects that can be pooled and reused.
 */
public interface Poolable {
    /**
     * Resets the object state for reuse.
     */
    void reset();
}
