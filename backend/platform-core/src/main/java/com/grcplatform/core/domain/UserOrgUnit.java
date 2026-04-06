package com.grcplatform.core.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** User membership in an org unit. Composite PK (user_id, org_unit_id). */
@Entity
@Table(name = "user_org_units")
public class UserOrgUnit {

    @Embeddable
    public static class Id implements java.io.Serializable {
        @Column(name = "user_id", nullable = false, updatable = false)
        private UUID userId;

        @Column(name = "org_unit_id", nullable = false, updatable = false)
        private UUID orgUnitId;

        public Id() {}

        public Id(UUID userId, UUID orgUnitId) {
            this.userId = userId;
            this.orgUnitId = orgUnitId;
        }

        public UUID getUserId() {
            return userId;
        }

        public UUID getOrgUnitId() {
            return orgUnitId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return java.util.Objects.equals(userId, id.userId)
                    && java.util.Objects.equals(orgUnitId, id.orgUnitId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(userId, orgUnitId);
        }
    }

    @EmbeddedId
    private Id id;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Column(name = "role_override")
    private String roleOverride;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    protected void prePersist() {
        if (joinedAt == null) joinedAt = Instant.now();
    }

    public static UserOrgUnit create(UUID userId, UUID orgUnitId, boolean primary) {
        var uou = new UserOrgUnit();
        uou.id = new Id(userId, orgUnitId);
        uou.primary = primary;
        return uou;
    }

    public Id getId() {
        return id;
    }

    public boolean isPrimary() {
        return primary;
    }

    public String getRoleOverride() {
        return roleOverride;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public void setRoleOverride(String roleOverride) {
        this.roleOverride = roleOverride;
    }
}
