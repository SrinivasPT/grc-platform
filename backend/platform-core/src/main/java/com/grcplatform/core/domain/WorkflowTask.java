package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_tasks")
public class WorkflowTask {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "instance_id", nullable = false, updatable = false)
    private UUID instanceId;

    @Column(name = "task_key", nullable = false, updatable = false)
    private String taskKey;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "assigned_group_id")
    private UUID assignedGroupId;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "escalation_days")
    private Integer escalationDays;

    @Column(name = "escalated_to")
    private UUID escalatedTo;

    @Column(name = "status", nullable = false)
    private String status = "pending";

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void prePersist() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public UUID getInstanceId() { return instanceId; }
    public String getTaskKey() { return taskKey; }
    public UUID getAssignedTo() { return assignedTo; }
    public UUID getAssignedGroupId() { return assignedGroupId; }
    public Instant getDueDate() { return dueDate; }
    public Integer getEscalationDays() { return escalationDays; }
    public UUID getEscalatedTo() { return escalatedTo; }
    public String getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
    public UUID getCompletedBy() { return completedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }

    public void setOrgId(UUID orgId) { this.orgId = orgId; }
    public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
    public void setTaskKey(String taskKey) { this.taskKey = taskKey; }
    public void setAssignedTo(UUID assignedTo) { this.assignedTo = assignedTo; }
    public void setAssignedGroupId(UUID assignedGroupId) { this.assignedGroupId = assignedGroupId; }
    public void setDueDate(Instant dueDate) { this.dueDate = dueDate; }
    public void setEscalationDays(Integer escalationDays) { this.escalationDays = escalationDays; }
    public void setEscalatedTo(UUID escalatedTo) { this.escalatedTo = escalatedTo; }
    public void setStatus(String status) { this.status = status; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setCompletedBy(UUID completedBy) { this.completedBy = completedBy; }
}
