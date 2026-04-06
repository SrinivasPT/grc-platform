package com.grcplatform.api.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import com.grcplatform.core.domain.RiskAppetiteThreshold;
import com.grcplatform.core.repository.RiskAppetiteThresholdRepository;

@Repository
public class RiskAppetiteThresholdRepositoryAdapter implements RiskAppetiteThresholdRepository {

    private final SpringRiskAppetiteThresholdRepository jpa;

    public RiskAppetiteThresholdRepositoryAdapter(SpringRiskAppetiteThresholdRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RiskAppetiteThreshold save(RiskAppetiteThreshold threshold) {
        return jpa.save(threshold);
    }

    @Override
    public Optional<RiskAppetiteThreshold> findActiveByOrgIdAndCategory(UUID orgId,
            String category) {
        return jpa.findActiveByOrgIdAndCategory(orgId, category);
    }

    @Override
    public List<RiskAppetiteThreshold> findActiveByOrgId(UUID orgId) {
        return jpa.findActiveByOrgId(orgId);
    }
}
