package com.grcplatform.api.repository;

import com.grcplatform.core.domain.EventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringEventOutboxRepository extends JpaRepository<EventOutbox, UUID> {

    @Query("""
            SELECT e FROM EventOutbox e
            WHERE e.status = 'PENDING'
            ORDER BY e.createdAt ASC
            """)
    List<EventOutbox> findPendingWithLimit(org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("UPDATE EventOutbox e SET e.status = 'PROCESSED', e.processedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    void markProcessed(@Param("id") UUID id);

    @Modifying
    @Query("""
            UPDATE EventOutbox e
            SET e.status = 'FAILED', e.lastError = :error, e.retryCount = e.retryCount + 1
            WHERE e.id = :id
            """)
    void markFailed(@Param("id") UUID id, @Param("error") String errorMessage);
}
