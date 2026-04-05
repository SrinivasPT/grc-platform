package com.grcplatform.api.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import com.grcplatform.core.domain.AuditLogEntry;
import com.grcplatform.core.repository.AuditLogRepository;

@Repository
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final SpringAuditLogRepository jpa;

    public AuditLogRepositoryAdapter(SpringAuditLogRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        return jpa.save(entry);
    }

    @Override
    public List<AuditLogEntry> findByEntityIdOrderBySequenceNumberAsc(UUID entityId, UUID orgId) {
        return jpa.findByEntityIdOrderBySequenceNumberAsc(entityId, orgId);
    }

    @Override
    public List<AuditLogEntry> findByEntityId(UUID entityId, UUID orgId, int limit, int offset) {
        var pageable =
                PageRequest.of(offset / limit, limit, Sort.by("sequenceNumber").descending());
        return jpa.findByEntityIdPaged(entityId, orgId, pageable).getContent();
    }

    @Override
    public long countByEntityId(UUID entityId, UUID orgId) {
        return jpa.countByEntityIdAndOrgId(entityId, orgId);
    }
}
