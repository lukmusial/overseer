package com.hft.core.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ListenerSetTest {

    private final ListenerSet.ExceptionSink SILENT = (l, t) -> {};

    @Test
    void emptySetNotifiesNobody() {
        var set = new ListenerSet<String>();
        assertEquals(0, set.size());
        set.notify("hi", SILENT);
    }

    @Test
    void notifiesAllRegisteredListenersInOrder() {
        var set = new ListenerSet<String>();
        var seen = new ArrayList<String>();
        set.add(s -> seen.add("a:" + s));
        set.add(s -> seen.add("b:" + s));
        set.add(s -> seen.add("c:" + s));
        set.notify("x", SILENT);
        assertEquals(List.of("a:x", "b:x", "c:x"), seen);
    }

    @Test
    void removeStopsNotifying() {
        var set = new ListenerSet<Integer>();
        var a = new AtomicInteger();
        var b = new AtomicInteger();
        Consumer<Integer> incA = a::addAndGet;
        Consumer<Integer> incB = b::addAndGet;
        set.add(incA);
        set.add(incB);
        set.notify(3, SILENT);
        assertEquals(3, a.get());
        assertEquals(3, b.get());

        assertTrue(set.remove(incA));
        set.notify(5, SILENT);
        assertEquals(3, a.get(), "removed listener must not receive further events");
        assertEquals(8, b.get());
    }

    @Test
    void removeUnknownReturnsFalse() {
        var set = new ListenerSet<String>();
        assertFalse(set.remove(x -> {}));
    }

    @Test
    void throwingListenerRoutesToSink() {
        var set = new ListenerSet<String>();
        var errors = new ArrayList<Throwable>();
        set.add(s -> { throw new RuntimeException("boom"); });
        var followupCalled = new AtomicInteger();
        set.add(s -> followupCalled.incrementAndGet());

        set.notify("hello", (listener, cause) -> errors.add(cause));

        assertEquals(1, errors.size(), "sink should see exactly one error");
        assertEquals("boom", errors.get(0).getMessage());
        assertEquals(1, followupCalled.get(), "subsequent listener must still fire");
    }

    @Test
    void sizeReflectsAddsAndRemoves() {
        var set = new ListenerSet<String>();
        Consumer<String> l1 = s -> {};
        Consumer<String> l2 = s -> {};
        assertEquals(0, set.size());
        set.add(l1);
        set.add(l2);
        assertEquals(2, set.size());
        set.remove(l1);
        assertEquals(1, set.size());
        set.remove(l2);
        assertEquals(0, set.size());
        assertTrue(set.isEmpty());
    }
}
