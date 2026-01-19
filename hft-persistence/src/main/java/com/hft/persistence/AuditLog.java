package com.hft.persistence;

import java.util.List;

/**
 * Interface for audit logging of trading events.
 */
public interface AuditLog {

    /**
     * Logs an event.
     */
    void log(AuditEvent event);

    /**
     * Logs an event with the given parameters.
     */
    default void log(EventType type, String message) {
        log(new AuditEvent(System.nanoTime(), type, message, null));
    }

    /**
     * Logs an event with details.
     */
    default void log(EventType type, String message, String details) {
        log(new AuditEvent(System.nanoTime(), type, message, details));
    }

    /**
     * Gets events for a date.
     */
    List<AuditEvent> getEventsForDate(int dateYYYYMMDD);

    /**
     * Gets recent events.
     */
    List<AuditEvent> getRecentEvents(int count);

    /**
     * Gets events by type.
     */
    List<AuditEvent> getEventsByType(EventType type, int count);

    /**
     * Flushes buffered events.
     */
    void flush();

    /**
     * Closes the audit log.
     */
    void close();

    /**
     * Audit event types.
     */
    enum EventType {
        // Order lifecycle
        ORDER_SUBMITTED,
        ORDER_ACCEPTED,
        ORDER_REJECTED,
        ORDER_FILLED,
        ORDER_PARTIALLY_FILLED,
        ORDER_CANCELLED,

        // Risk events
        RISK_CHECK_PASSED,
        RISK_CHECK_FAILED,
        CIRCUIT_BREAKER_TRIPPED,
        CIRCUIT_BREAKER_RESET,
        TRADING_DISABLED,
        TRADING_ENABLED,

        // Position events
        POSITION_OPENED,
        POSITION_CLOSED,
        POSITION_UPDATED,

        // System events
        ENGINE_STARTED,
        ENGINE_STOPPED,
        STRATEGY_STARTED,
        STRATEGY_STOPPED,
        CONNECTION_ESTABLISHED,
        CONNECTION_LOST,

        // Errors
        ERROR,
        WARNING
    }

    /**
     * Immutable audit event.
     */
    record AuditEvent(
            long timestampNanos,
            EventType type,
            String message,
            String details
    ) {}
}
