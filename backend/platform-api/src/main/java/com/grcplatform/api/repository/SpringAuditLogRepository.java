package com.grcplatform.api.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.grcplatform.core.domain.AuditLogEntry;

interface SpringAuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    @Query("""
            SELECT a FROM AuditLogEntry a
            WHERE a.entityId = :eid AND a.orgId = :oid
            ORDER BY a.sequenceNumber ASC
            """)
    List<AuditLogEntry> findByEntityIdOrderBySequenceNumberAsc(@Param("eid") UUID entityId,
            @Param("oid") UUID orgId);

    @Query("""
            SELECT a FROM AuditLogEntry a
            WHERE a.entityId = :eid AND a.orgId = :oid
            ORDER BY a.sequenceNumber DESC
            """)
    org.springframework.data.domain.Page<AuditLogEntry> findByEntityIdPaged(
            @Param("eid") UUID entityId, @Param("oid") UUID orgId,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuditLogEntry a WHERE a.entityId = :eid AND a.orgId = :oid")
    long countByEntityIdAndOrgId(@Param("eid") UUID entityId, @Param("oid") UUID orgId);
}
