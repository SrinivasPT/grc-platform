package com.grcplatform.core.repository;

import com.grcplatform.core.domain.AuditLogEntry;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository {

    AuditLogEntry save(AuditLogEntry entry);

    List<AuditLogEntry> findByEntityIdOrderBySequenceNumberAsc(UUID entityId, UUID orgId);

    List<AuditLogEntry> findByEntityId(UUID entityId, UUID orgId, int limit, int offset);

    long countByEntityId(UUID entityId, UUID orgId);
}
