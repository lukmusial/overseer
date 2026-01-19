package com.hft.persistence.chronicle;

import com.hft.persistence.AuditLog;
import net.openhft.chronicle.wire.SelfDescribingMarshallable;

/**
 * Chronicle Wire format for AuditEvent serialization.
 */
public class AuditEventWire extends SelfDescribingMarshallable {
    private long timestampNanos;
    private String type;
    private String message;
    private String details;

    public AuditEventWire() {
    }

    public static AuditEventWire from(AuditLog.AuditEvent event) {
        AuditEventWire wire = new AuditEventWire();
        wire.timestampNanos = event.timestampNanos();
        wire.type = event.type().name();
        wire.message = event.message();
        wire.details = event.details();
        return wire;
    }

    public AuditLog.AuditEvent toEvent() {
        return new AuditLog.AuditEvent(
                timestampNanos,
                AuditLog.EventType.valueOf(type),
                message,
                details
        );
    }

    public long getTimestampNanos() { return timestampNanos; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getDetails() { return details; }
}
