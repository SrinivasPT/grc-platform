package com.grcplatform.risk;

import com.grcplatform.risk.RiskScore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskScoreRepository {

    RiskScore save(RiskScore score);

    Optional<RiskScore> findByRecordIdAndOrgId(UUID recordId, UUID orgId);

    /** Bulk load for BatchMapping resolver. */
    List<RiskScore> findByOrgIdAndRecordIdIn(UUID orgId, List<UUID> recordIds);
}
