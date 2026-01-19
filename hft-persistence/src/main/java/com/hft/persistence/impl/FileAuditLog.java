package com.hft.persistence.impl;

import com.hft.persistence.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * File-based audit log implementation.
 * Writes audit events to daily log files.
 */
public class FileAuditLog implements AuditLog {
    private static final Logger log = LoggerFactory.getLogger(FileAuditLog.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Path baseDir;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Deque<AuditEvent> recentEvents = new ArrayDeque<>();
    private final int maxRecentEvents;

    private BufferedWriter currentWriter;
    private int currentDateYYYYMMDD;

    public FileAuditLog(Path baseDir) {
        this(baseDir, 1000);
    }

    public FileAuditLog(Path baseDir, int maxRecentEvents) {
        this.baseDir = baseDir;
        this.maxRecentEvents = maxRecentEvents;

        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create audit log directory", e);
        }

        log.info("Audit log initialized at {}", baseDir);
    }

    @Override
    public void log(AuditEvent event) {
        writeLock.lock();
        try {
            ensureWriterForToday();
            writeEvent(event);

            recentEvents.addFirst(event);
            while (recentEvents.size() > maxRecentEvents) {
                recentEvents.removeLast();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public List<AuditEvent> getEventsForDate(int dateYYYYMMDD) {
        Path file = getFileForDate(dateYYYYMMDD);
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }

        List<AuditEvent> events = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                AuditEvent event = parseEvent(line);
                if (event != null) {
                    events.add(event);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read audit events for date {}", dateYYYYMMDD, e);
        }
        return events;
    }

    @Override
    public List<AuditEvent> getRecentEvents(int count) {
        writeLock.lock();
        try {
            return recentEvents.stream()
                    .limit(count)
                    .collect(Collectors.toList());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public List<AuditEvent> getEventsByType(EventType type, int count) {
        writeLock.lock();
        try {
            return recentEvents.stream()
                    .filter(e -> e.type() == type)
                    .limit(count)
                    .collect(Collectors.toList());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void flush() {
        writeLock.lock();
        try {
            if (currentWriter != null) {
                currentWriter.flush();
            }
        } catch (IOException e) {
            log.error("Failed to flush audit log", e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        writeLock.lock();
        try {
            if (currentWriter != null) {
                currentWriter.close();
                currentWriter = null;
            }
        } catch (IOException e) {
            log.error("Failed to close audit log", e);
        } finally {
            writeLock.unlock();
        }
    }

    private void ensureWriterForToday() {
        int today = Integer.parseInt(LocalDate.now().format(DATE_FORMAT));
        if (currentWriter == null || today != currentDateYYYYMMDD) {
            closeCurrentWriter();
            currentDateYYYYMMDD = today;
            try {
                Path file = getFileForDate(today);
                currentWriter = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open audit log file", e);
            }
        }
    }

    private void closeCurrentWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (IOException e) {
                log.warn("Failed to close previous audit log writer", e);
            }
            currentWriter = null;
        }
    }

    private void writeEvent(AuditEvent event) {
        try {
            // Format: timestamp|type|message|details
            StringBuilder sb = new StringBuilder();
            sb.append(event.timestampNanos()).append('|');
            sb.append(event.type().name()).append('|');
            sb.append(escape(event.message())).append('|');
            sb.append(escape(event.details()));
            currentWriter.write(sb.toString());
            currentWriter.newLine();
        } catch (IOException e) {
            log.error("Failed to write audit event", e);
        }
    }

    private AuditEvent parseEvent(String line) {
        try {
            String[] parts = line.split("\\|", 4);
            if (parts.length < 3) {
                return null;
            }

            long timestamp = Long.parseLong(parts[0]);
            EventType type = EventType.valueOf(parts[1]);
            String message = unescape(parts[2]);
            String details = parts.length > 3 ? unescape(parts[3]) : null;

            return new AuditEvent(timestamp, type, message, details);
        } catch (Exception e) {
            log.warn("Failed to parse audit event: {}", line, e);
            return null;
        }
    }

    private Path getFileForDate(int dateYYYYMMDD) {
        return baseDir.resolve("audit_" + dateYYYYMMDD + ".log");
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\n", "\\n");
    }

    private String unescape(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.replace("\\n", "\n")
                .replace("\\|", "|")
                .replace("\\\\", "\\");
    }
}
