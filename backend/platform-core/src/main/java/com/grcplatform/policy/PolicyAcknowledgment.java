package com.grcplatform.policy;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

/** Tracks a user's formal acknowledgment of a specific policy version. */
@Entity
@Table(name = "policy_acknowledgments")
@Getter
public class PolicyAcknowledgment {

    @jakarta.persistence.Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "policy_record_id", nullable = false, updatable = false)
    private UUID policyRecordId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "acknowledged_at", nullable = false, updatable = false)
    private Instant acknowledgedAt;

    @Column(name = "policy_version", nullable = false, updatable = false)
    private String policyVersion;

    @Column(name = "method", nullable = false)
    private String method = "platform";

    @Column(name = "ip_address")
    private String ipAddress;

    @PrePersist
    protected void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (acknowledgedAt == null) acknowledgedAt = Instant.now();
    }

    public static PolicyAcknowledgment create(UUID orgId, UUID policyRecordId, UUID userId,
            String policyVersion, String method, String ipAddress) {
        var ack = new PolicyAcknowledgment();
        ack.orgId = orgId;
        ack.policyRecordId = policyRecordId;
        ack.userId = userId;
        ack.policyVersion = policyVersion;
        ack.method = method != null ? method : "platform";
        ack.ipAddress = ipAddress;
        return ack;
    }

    /** Projects this entity to its API DTO. */
    public PolicyAcknowledgmentDto toDto() {
        return new PolicyAcknowledgmentDto(id, policyRecordId, userId, acknowledgedAt,
                policyVersion, method);
    }
}
