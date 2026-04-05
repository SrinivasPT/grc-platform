package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "field_values_reference")
public class FieldValueReference {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(name = "field_def_id", nullable = false, updatable = false)
    private UUID fieldDefId;

    @Column(name = "ref_type", nullable = false, updatable = false)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private UUID refId;

    @Column(name = "display_label")
    private String displayLabel;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void prePersist() {
        if (id == null)
            id = UUID.randomUUID();
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

    public UUID getRecordId() {
        return recordId;
    }

    public UUID getFieldDefId() {
        return fieldDefId;
    }

    public String getRefType() {
        return refType;
    }

    public UUID getRefId() {
        return refId;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public static FieldValueReference of(UUID orgId, UUID recordId, UUID fieldDefId, String refType,
            UUID refId, String displayLabel) {
        FieldValueReference fv = new FieldValueReference();
        fv.orgId = orgId;
        fv.recordId = recordId;
        fv.fieldDefId = fieldDefId;
        fv.refType = refType;
        fv.refId = refId;
        fv.displayLabel = displayLabel;
        return fv;
    }
}
