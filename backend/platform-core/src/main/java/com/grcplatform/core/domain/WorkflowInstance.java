package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_instances")
public class WorkflowInstance {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(name = "definition_id", nullable = false, updatable = false)
    private UUID definitionId;

    @Column(name = "current_state", nullable = false)
    private String currentState;

    @Column(name = "status", nullable = false)
    private String status = "active";

    @Column(name = "entered_state_at", nullable = false)
    private Instant enteredStateAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @PrePersist
    protected void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        enteredStateAt = now;
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

    public UUID getDefinitionId() {
        return definitionId;
    }

    public String getCurrentState() {
        return currentState;
    }

    public String getStatus() {
        return status;
    }

    public Instant getEnteredStateAt() {
        return enteredStateAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    public void setRecordId(UUID recordId) {
        this.recordId = recordId;
    }

    public void setDefinitionId(UUID definitionId) {
        this.definitionId = definitionId;
    }

    public void setCurrentState(String currentState) {
        this.currentState = currentState;
        this.enteredStateAt = Instant.now();
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
