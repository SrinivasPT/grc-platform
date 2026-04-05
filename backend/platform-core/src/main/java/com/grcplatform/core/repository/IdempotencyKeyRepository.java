package com.grcplatform.core.repository;

import com.grcplatform.core.domain.IdempotencyKey;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyKeyRepository {

    Optional<IdempotencyKey> findByKeyHashIfNotExpired(String keyHash, Instant now);

    void save(IdempotencyKey key);

    /** Removes all entries whose expires_at is before the given threshold. */
    void deleteExpiredBefore(Instant threshold);
}
