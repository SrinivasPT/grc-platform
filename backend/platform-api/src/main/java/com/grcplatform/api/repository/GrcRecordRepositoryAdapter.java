package com.grcplatform.api.repository;

import com.grcplatform.core.domain.GrcRecord;
import com.grcplatform.core.repository.GrcRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GrcRecordRepositoryAdapter implements GrcRecordRepository {

    private final SpringGrcRecordRepository jpa;

    public GrcRecordRepositoryAdapter(SpringGrcRecordRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public GrcRecord save(GrcRecord record) {
        return jpa.save(record);
    }

    @Override
    public Optional<GrcRecord> findByIdAndOrgId(UUID id, UUID orgId) {
        return jpa.findByIdAndOrgId(id, orgId);
    }

    @Override
    public List<GrcRecord> findByApplicationIdAndOrgId(UUID applicationId, UUID orgId, int offset,
            int limit) {
        var pageable = PageRequest.of(offset / limit, limit);
        return jpa.findActiveByApplicationIdAndOrgId(applicationId, orgId, pageable).getContent();
    }

    @Override
    public long countByApplicationIdAndOrgId(UUID applicationId, UUID orgId) {
        return jpa.countActiveByApplicationIdAndOrgId(applicationId, orgId);
    }

    @Override
    public int nextRecordNumber(UUID orgId, UUID applicationId) {
        return jpa.nextRecordNumber(orgId, applicationId);
    }
}
