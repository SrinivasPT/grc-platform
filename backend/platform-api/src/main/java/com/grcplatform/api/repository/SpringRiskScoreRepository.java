package com.grcplatform.api.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.grcplatform.core.domain.RiskScore;

interface SpringRiskScoreRepository extends JpaRepository<RiskScore, UUID> {

    Optional<RiskScore> findByRecordIdAndOrgId(UUID recordId, UUID orgId);

    List<RiskScore> findByOrgIdAndRecordIdIn(UUID orgId, List<UUID> recordIds);
}
