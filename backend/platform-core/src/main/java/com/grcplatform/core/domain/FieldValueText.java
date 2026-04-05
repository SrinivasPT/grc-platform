package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "field_values_text")
public class FieldValueText {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(name = "field_def_id", nullable = false, updatable = false)
    private UUID fieldDefId;

    @Column(name = "value")
    private String value;

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

    public String getValue() {
        return value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public static FieldValueText of(UUID orgId, UUID recordId, UUID fieldDefId, String value) {
        FieldValueText fv = new FieldValueText();
        fv.orgId = orgId;
        fv.recordId = recordId;
        fv.fieldDefId = fieldDefId;
        fv.value = value;
        return fv;
    }
}
