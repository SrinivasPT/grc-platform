package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_definitions")
public class WorkflowDefinition {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "config", nullable = false)
    private String config;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public UUID getApplicationId() { return applicationId; }
    public String getName() { return name; }
    public String getConfig() { return config; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    public void setOrgId(UUID orgId) { this.orgId = orgId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public void setName(String name) { this.name = name; }
    public void setConfig(String config) { this.config = config; }
    public void setActive(boolean active) { this.active = active; }
}
