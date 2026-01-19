package com.hft.core.model;

import org.agrona.collections.ObjectHashSet;

import java.util.function.Supplier;

/**
 * Simple object pool for reducing GC pressure.
 * Thread-local for lock-free access.
 *
 * @param <T> Type of pooled objects
 */
public class ObjectPool<T extends Poolable> {
    private final ThreadLocal<PooledObjects<T>> threadLocalPool;
    private final Supplier<T> factory;
    private final int maxSize;

    public ObjectPool(Supplier<T> factory, int maxSize) {
        this.factory = factory;
        this.maxSize = maxSize;
        this.threadLocalPool = ThreadLocal.withInitial(() -> new PooledObjects<>(maxSize));
    }

    /**
     * Acquires an object from the pool or creates a new one.
     */
    public T acquire() {
        PooledObjects<T> pool = threadLocalPool.get();
        T obj = pool.poll();
        if (obj == null) {
            obj = factory.get();
        }
        return obj;
    }

    /**
     * Returns an object to the pool after resetting it.
     */
    public void release(T obj) {
        if (obj == null) {
            return;
        }
        obj.reset();
        PooledObjects<T> pool = threadLocalPool.get();
        pool.offer(obj);
    }

    /**
     * Pre-allocates objects in the pool for the current thread.
     */
    public void preallocate(int count) {
        PooledObjects<T> pool = threadLocalPool.get();
        for (int i = 0; i < count && pool.size() < maxSize; i++) {
            pool.offer(factory.get());
        }
    }

    private static class PooledObjects<T> {
        private final Object[] objects;
        private int head = 0;
        private int tail = 0;
        private int count = 0;
        private final int capacity;

        PooledObjects(int capacity) {
            this.capacity = capacity;
            this.objects = new Object[capacity];
        }

        @SuppressWarnings("unchecked")
        T poll() {
            if (count == 0) {
                return null;
            }
            T obj = (T) objects[head];
            objects[head] = null;
            head = (head + 1) % capacity;
            count--;
            return obj;
        }

        void offer(T obj) {
            if (count >= capacity) {
                return; // Pool is full, discard
            }
            objects[tail] = obj;
            tail = (tail + 1) % capacity;
            count++;
        }

        int size() {
            return count;
        }
    }
}
