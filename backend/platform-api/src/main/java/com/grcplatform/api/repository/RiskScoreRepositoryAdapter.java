package com.grcplatform.api.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import com.grcplatform.core.domain.RiskScore;
import com.grcplatform.core.repository.RiskScoreRepository;

@Repository
public class RiskScoreRepositoryAdapter implements RiskScoreRepository {

    private final SpringRiskScoreRepository jpa;

    public RiskScoreRepositoryAdapter(SpringRiskScoreRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RiskScore save(RiskScore score) {
        return jpa.save(score);
    }

    @Override
    public Optional<RiskScore> findByRecordIdAndOrgId(UUID recordId, UUID orgId) {
        return jpa.findByRecordIdAndOrgId(recordId, orgId);
    }

    @Override
    public List<RiskScore> findByOrgIdAndRecordIdIn(UUID orgId, List<UUID> recordIds) {
        return jpa.findByOrgIdAndRecordIdIn(orgId, recordIds);
    }
}
