package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "record_relations")
public class RecordRelation {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(name = "relation_type", nullable = false, updatable = false)
    private String relationType;

    @Column(name = "is_directional", nullable = false)
    private boolean directional = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "metadata")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @PrePersist
    protected void prePersist() {
        if (id == null)
            id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getRelationType() {
        return relationType;
    }

    public boolean isDirectional() {
        return directional;
    }

    public boolean isActive() {
        return active;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public static RecordRelation create(UUID orgId, UUID sourceId, UUID targetId,
            String relationType, UUID createdBy) {
        RecordRelation rr = new RecordRelation();
        rr.orgId = orgId;
        rr.sourceId = sourceId;
        rr.targetId = targetId;
        rr.relationType = relationType;
        rr.createdBy = createdBy;
        return rr;
    }
}
