package com.grcplatform.core.repository;

import com.grcplatform.core.domain.GrcRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrcRecordRepository {

    GrcRecord save(GrcRecord record);

    Optional<GrcRecord> findByIdAndOrgId(UUID id, UUID orgId);

    List<GrcRecord> findByApplicationIdAndOrgId(UUID applicationId, UUID orgId, int offset,
            int limit);

    long countByApplicationIdAndOrgId(UUID applicationId, UUID orgId);

    int nextRecordNumber(UUID orgId, UUID applicationId);
}
