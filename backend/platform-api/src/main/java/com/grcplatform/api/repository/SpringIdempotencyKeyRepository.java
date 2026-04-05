package com.grcplatform.api.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.grcplatform.core.domain.IdempotencyKey;

interface SpringIdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    @Query("""
            SELECT k FROM IdempotencyKey k
            WHERE k.keyHash = :hash AND k.expiresAt > :now
            """)
    Optional<IdempotencyKey> findByKeyHashIfNotExpired(@Param("hash") String keyHash,
            @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.expiresAt < :threshold")
    void deleteExpiredBefore(@Param("threshold") Instant threshold);
}
