package com.grcplatform.api.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import com.grcplatform.core.domain.IdempotencyKey;
import com.grcplatform.core.repository.IdempotencyKeyRepository;

@Repository
public class IdempotencyKeyRepositoryAdapter implements IdempotencyKeyRepository {

    private final SpringIdempotencyKeyRepository jpa;

    public IdempotencyKeyRepositoryAdapter(SpringIdempotencyKeyRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<IdempotencyKey> findByKeyHashIfNotExpired(String keyHash, Instant now) {
        return jpa.findByKeyHashIfNotExpired(keyHash, now);
    }

    @Override
    public void save(IdempotencyKey key) {
        jpa.save(key);
    }

    @Override
    public void deleteExpiredBefore(Instant threshold) {
        jpa.deleteExpiredBefore(threshold);
    }
}
