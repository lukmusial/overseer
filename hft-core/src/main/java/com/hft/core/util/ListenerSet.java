package com.hft.core.util;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Concurrent listener container optimised for a hot dispatch path with rare
 * subscription changes — the listener set grows/shrinks at startup or on
 * configuration events, but notify() fires per-tick.
 *
 * <p>Compared to {@link java.util.concurrent.CopyOnWriteArrayList}:
 * <ul>
 *   <li>{@link #notify(Object)} uses an indexed loop over a volatile array,
 *       with no iterator allocation per call. CoW's enhanced-for allocates
 *       a {@code COWIterator} on every traversal.</li>
 *   <li>No {@code List} contract overhead: the only supported operations are
 *       add/remove/notify.</li>
 * </ul>
 *
 * <p>Thread-safety model: writers serialise through the object monitor; the
 * latest snapshot is published via a volatile write and consumed via a
 * volatile read. A listener observed during a notify() call is one of
 * the listeners present at the moment notify() loaded the snapshot.
 */
public final class ListenerSet<T> {

    @SuppressWarnings("unchecked")
    private volatile Consumer<T>[] snapshot = (Consumer<T>[]) new Consumer[0];

    public synchronized void add(Consumer<T> listener) {
        Objects.requireNonNull(listener, "listener");
        Consumer<T>[] current = snapshot;
        @SuppressWarnings("unchecked")
        Consumer<T>[] next = (Consumer<T>[]) new Consumer[current.length + 1];
        System.arraycopy(current, 0, next, 0, current.length);
        next[current.length] = listener;
        snapshot = next;
    }

    public synchronized boolean remove(Consumer<T> listener) {
        Consumer<T>[] current = snapshot;
        int idx = -1;
        for (int i = 0; i < current.length; i++) {
            if (current[i].equals(listener)) { idx = i; break; }
        }
        if (idx < 0) return false;
        @SuppressWarnings("unchecked")
        Consumer<T>[] next = (Consumer<T>[]) new Consumer[current.length - 1];
        System.arraycopy(current, 0, next, 0, idx);
        System.arraycopy(current, idx + 1, next, idx, current.length - 1 - idx);
        snapshot = next;
        return true;
    }

    /**
     * Hot-path notify: one volatile load, indexed loop. A thrown exception
     * from one listener does not prevent the remaining listeners from seeing
     * the event — callers typically prefer this for isolation.
     */
    public void notify(T event, ExceptionSink sink) {
        Consumer<T>[] ls = snapshot;
        for (int i = 0; i < ls.length; i++) {
            try {
                ls[i].accept(event);
            } catch (Throwable t) {
                sink.onError(ls[i], t);
            }
        }
    }

    public int size() {
        return snapshot.length;
    }

    public boolean isEmpty() {
        return snapshot.length == 0;
    }

    @FunctionalInterface
    public interface ExceptionSink {
        void onError(Object listener, Throwable cause);
    }
}
