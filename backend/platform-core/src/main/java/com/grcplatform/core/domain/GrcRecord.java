package com.grcplatform.core.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A GrcRecord is one runtime instance of an Application entity. Named GrcRecord (not Record) to
 * avoid confusion with java.lang.Record.
 */
@Entity
@Table(name = "records")
public class GrcRecord extends BaseEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "record_number", nullable = false, updatable = false)
    private int recordNumber;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "display_number")
    private String displayNumber;

    @Column(name = "status", nullable = false)
    private String status = "active";

    @Column(name = "workflow_state")
    private String workflowState;

    @Column(name = "computed_values")
    private String computedValues;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "graph_updated_at")
    private Instant graphUpdatedAt;

    public UUID getApplicationId() {
        return applicationId;
    }

    public int getRecordNumber() {
        return recordNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayNumber() {
        return displayNumber;
    }

    public String getStatus() {
        return status;
    }

    public String getWorkflowState() {
        return workflowState;
    }

    public String getComputedValues() {
        return computedValues;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getGraphUpdatedAt() {
        return graphUpdatedAt;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setDisplayNumber(String displayNumber) {
        this.displayNumber = displayNumber;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setWorkflowState(String workflowState) {
        this.workflowState = workflowState;
    }

    public void setComputedValues(String computedValues) {
        this.computedValues = computedValues;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public void softDelete(Instant now) {
        this.deleted = true;
        this.deletedAt = now;
    }

    public static GrcRecord create(UUID orgId, UUID applicationId, int recordNumber,
            String displayNumber, UUID createdBy) {
        GrcRecord record = new GrcRecord();
        record.setOrgId(orgId);
        record.applicationId = applicationId;
        record.recordNumber = recordNumber;
        record.displayNumber = displayNumber;
        record.createdBy = createdBy;
        record.updatedBy = createdBy;
        return record;
    }
}
