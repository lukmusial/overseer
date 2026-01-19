package com.hft.persistence.chronicle;

import com.hft.persistence.AuditLog;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Chronicle Queue based audit log for high-performance event logging.
 *
 * Features:
 * - Zero-GC writes
 * - Automatic daily rolling
 * - Fast replay capability
 * - Memory-mapped persistence
 */
public class ChronicleAuditLog implements AuditLog {
    private static final Logger log = LoggerFactory.getLogger(ChronicleAuditLog.class);

    private final ChronicleQueue queue;
    private final ExcerptAppender appender;
    private final Deque<AuditEvent> recentEvents = new ConcurrentLinkedDeque<>();
    private final int maxRecentEvents;

    public ChronicleAuditLog(Path basePath) {
        this(basePath, 1000);
    }

    public ChronicleAuditLog(Path basePath, int maxRecentEvents) {
        this.maxRecentEvents = maxRecentEvents;

        this.queue = ChronicleQueue.singleBuilder(basePath.resolve("audit"))
                .build();
        this.appender = queue.createAppender();

        log.info("Chronicle audit log initialized at {}", basePath);
    }

    @Override
    public void log(AuditEvent event) {
        AuditEventWire wire = AuditEventWire.from(event);

        appender.writeDocument(w -> w.write("event").marshallable(wire));

        // Keep recent events in memory
        recentEvents.addFirst(event);
        while (recentEvents.size() > maxRecentEvents) {
            recentEvents.removeLast();
        }
    }

    @Override
    public List<AuditEvent> getEventsForDate(int dateYYYYMMDD) {
        List<AuditEvent> events = new ArrayList<>();

        LocalDate date = parseDate(dateYYYYMMDD);
        long startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000;
        long endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000;

        try (ExcerptTailer tailer = queue.createTailer()) {
            AuditEventWire wire = new AuditEventWire();

            while (tailer.readDocument(w -> w.read("event").marshallable(wire))) {
                if (wire.getTimestampNanos() >= startOfDay && wire.getTimestampNanos() < endOfDay) {
                    events.add(wire.toEvent());
                }
            }
        }

        return events;
    }

    @Override
    public List<AuditEvent> getRecentEvents(int count) {
        List<AuditEvent> result = new ArrayList<>();
        int added = 0;
        for (AuditEvent event : recentEvents) {
            if (added >= count) break;
            result.add(event);
            added++;
        }
        return result;
    }

    @Override
    public List<AuditEvent> getEventsByType(EventType type, int count) {
        List<AuditEvent> result = new ArrayList<>();
        for (AuditEvent event : recentEvents) {
            if (event.type() == type) {
                result.add(event);
                if (result.size() >= count) break;
            }
        }
        return result;
    }

    @Override
    public void flush() {
        // Chronicle auto-flushes
    }

    @Override
    public void close() {
        log.info("Closing Chronicle audit log");
        queue.close();
    }

    /**
     * Replays all events through a handler.
     */
    public void replay(EventHandler handler) {
        try (ExcerptTailer tailer = queue.createTailer()) {
            AuditEventWire wire = new AuditEventWire();

            while (tailer.readDocument(w -> w.read("event").marshallable(wire))) {
                handler.onEvent(wire.toEvent());
            }
        }
    }

    private LocalDate parseDate(int dateYYYYMMDD) {
        int year = dateYYYYMMDD / 10000;
        int month = (dateYYYYMMDD % 10000) / 100;
        int day = dateYYYYMMDD % 100;
        return LocalDate.of(year, month, day);
    }

    /**
     * Handler for event replay.
     */
    @FunctionalInterface
    public interface EventHandler {
        void onEvent(AuditEvent event);
    }
}
