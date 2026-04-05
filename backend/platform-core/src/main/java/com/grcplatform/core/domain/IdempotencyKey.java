package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores hashed idempotency keys to deduplicate state-changing API calls. key_hash = SHA-256(org_id
 * + ":" + raw_idempotency_key) to prevent cross-org collisions. Entries expire after 24 hours and
 * are cleaned up by a scheduled job.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "key_hash", length = 64, updatable = false, nullable = false)
    private String keyHash;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "response_body", nullable = false, updatable = false)
    private String responseBody;

    @Column(name = "status_code", nullable = false, updatable = false)
    private int statusCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected IdempotencyKey() {}

    public static IdempotencyKey of(String keyHash, UUID orgId, String responseBody, int statusCode,
            Instant expiresAt) {
        var key = new IdempotencyKey();
        key.keyHash = keyHash;
        key.orgId = orgId;
        key.responseBody = responseBody;
        key.statusCode = statusCode;
        key.createdAt = Instant.now();
        key.expiresAt = expiresAt;
        return key;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
