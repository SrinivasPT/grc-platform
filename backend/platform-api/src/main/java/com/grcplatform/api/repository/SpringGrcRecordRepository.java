package com.grcplatform.api.repository;

import com.grcplatform.core.domain.GrcRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringGrcRecordRepository extends JpaRepository<GrcRecord, UUID> {

    Optional<GrcRecord> findByIdAndOrgId(UUID id, UUID orgId);

    @Query("""
            SELECT r FROM GrcRecord r
            WHERE r.applicationId = :appId AND r.orgId = :orgId AND r.deleted = false
            """)
    Page<GrcRecord> findActiveByApplicationIdAndOrgId(@Param("appId") UUID applicationId,
            @Param("orgId") UUID orgId, Pageable pageable);

    @Query("""
            SELECT COUNT(r) FROM GrcRecord r
            WHERE r.applicationId = :appId AND r.orgId = :orgId AND r.deleted = false
            """)
    long countActiveByApplicationIdAndOrgId(@Param("appId") UUID applicationId,
            @Param("orgId") UUID orgId);

    @Query(value = """
            SELECT COALESCE(MAX(r.record_number), 0) + 1
            FROM records r
            WHERE r.org_id = :orgId AND r.application_id = :appId
            """, nativeQuery = true)
    int nextRecordNumber(@Param("orgId") UUID orgId, @Param("appId") UUID applicationId);
}
