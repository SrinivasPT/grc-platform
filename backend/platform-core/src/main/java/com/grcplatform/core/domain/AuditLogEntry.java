package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequenceNumber;

    @Column(name = "event_hash", nullable = false, updatable = false, length = 64)
    private String eventHash;

    @Column(name = "prev_hash", nullable = false, updatable = false, length = 64)
    private String prevHash;

    @Column(name = "event_time", nullable = false, updatable = false)
    private Instant eventTime;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "old_value", updatable = false)
    private String oldValue;

    @Column(name = "new_value", updatable = false)
    private String newValue;

    @Column(name = "ip_address", updatable = false)
    private String ipAddress;

    @Column(name = "session_id", updatable = false)
    private String sessionId;

    @Column(name = "correlation_id", updatable = false)
    private UUID correlationId;

    @PrePersist
    protected void prePersist() {
        if (id == null)
            id = UUID.randomUUID();
        if (eventTime == null)
            eventTime = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getEventHash() {
        return eventHash;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getSessionId() {
        return sessionId;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public static AuditLogEntry create(UUID orgId, long sequenceNumber, String eventHash,
            String prevHash, UUID userId, String entityType, UUID entityId, String action,
            String oldValue, String newValue, UUID correlationId) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.orgId = orgId;
        entry.sequenceNumber = sequenceNumber;
        entry.eventHash = eventHash;
        entry.prevHash = prevHash;
        entry.userId = userId;
        entry.entityType = entityType;
        entry.entityId = entityId;
        entry.action = action;
        entry.oldValue = oldValue;
        entry.newValue = newValue;
        entry.correlationId = correlationId;
        return entry;
    }
}
