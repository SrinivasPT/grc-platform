package com.grcplatform.core.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "graph_sync_state")
public class GraphSyncState {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "last_ct_version", nullable = false)
    private long lastCtVersion = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void prePersist() {
        if (id == null) id = UUID.randomUUID();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getEntityType() {
        return entityType;
    }

    public long getLastCtVersion() {
        return lastCtVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public void setLastCtVersion(long version) {
        this.lastCtVersion = version;
    }

    public static GraphSyncState create(UUID orgId, String entityType) {
        GraphSyncState s = new GraphSyncState();
        s.orgId = orgId;
        s.entityType = entityType;
        return s;
    }
}
