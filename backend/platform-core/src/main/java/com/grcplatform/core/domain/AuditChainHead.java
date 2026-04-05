package com.grcplatform.core.domain;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * One row per org — the optimistic-lock anchor for the audit hash chain. See plan P1.5 and
 * ADR-005/ADR-009.
 */
@Entity
@Table(name = "audit_chain_head")
public class AuditChainHead {

    @Id
    @Column(name = "org_id", updatable = false, nullable = false)
    private UUID orgId;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence = 0;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    public UUID getOrgId() {
        return orgId;
    }

    public long getLastSequence() {
        return lastSequence;
    }

    public String getLastHash() {
        return lastHash;
    }

    public int getVersion() {
        return version;
    }

    public long advanceSequence() {
        lastSequence++;
        return lastSequence;
    }

    public void updateHash(String newHash) {
        this.lastHash = newHash;
    }

    public static AuditChainHead initFor(UUID orgId) {
        AuditChainHead head = new AuditChainHead();
        head.orgId = orgId;
        head.lastSequence = 0;
        head.lastHash = "0".repeat(64);
        return head;
    }
}
