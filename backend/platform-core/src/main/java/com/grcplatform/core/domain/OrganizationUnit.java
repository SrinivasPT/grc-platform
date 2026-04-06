package com.grcplatform.core.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * An org unit in the bank's internal structure (division, department, branch, etc.). Uses
 * materialized-path for O(1) subtree queries.
 */
@Entity
@Table(name = "organization_units")
public class OrganizationUnit extends BaseEntity {

    @Column(name = "parent_id")
    private UUID parentId;

    /** Materialized path: '/rootId/childId/grandchildId/' — each segment is a UUID (no hyphens). */
    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "unit_type", nullable = false)
    private String unitType;

    @Column(name = "code")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "hr_dept_code")
    private String hrDeptCode;

    // ─── Getters ──────────────────────────────────────────────────────────────

    public UUID getParentId() {
        return parentId;
    }

    public String getPath() {
        return path;
    }

    public int getDepth() {
        return depth;
    }

    public String getUnitType() {
        return unitType;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public String getHrDeptCode() {
        return hrDeptCode;
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setManagerId(UUID managerId) {
        this.managerId = managerId;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setHrDeptCode(String hrDeptCode) {
        this.hrDeptCode = hrDeptCode;
    }

    // expose id setter for factory callers
    public void setId(UUID id) {
        super.setId(id);
    }

    /** Static factory — sets all required fields from a creation command. */
    public static OrganizationUnit create(UUID orgId, UUID parentId, String path, int depth,
            String unitType, String code, String name, String description, UUID managerId) {
        var unit = new OrganizationUnit();
        unit.setOrgId(orgId);
        unit.parentId = parentId;
        unit.path = path;
        unit.depth = depth;
        unit.unitType = unitType;
        unit.code = code;
        unit.name = name;
        unit.description = description;
        unit.managerId = managerId;
        return unit;
    }
}
