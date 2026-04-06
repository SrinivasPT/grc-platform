package com.grcplatform.api.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.grcplatform.core.domain.RiskAppetiteThreshold;

interface SpringRiskAppetiteThresholdRepository extends JpaRepository<RiskAppetiteThreshold, UUID> {

    @Query("""
            SELECT t FROM RiskAppetiteThreshold t
            WHERE t.orgId = :orgId
            AND (:category IS NULL AND t.category IS NULL OR t.category = :category)
            AND t.effectiveTo IS NULL
            """)
    Optional<RiskAppetiteThreshold> findActiveByOrgIdAndCategory(@Param("orgId") UUID orgId,
            @Param("category") String category);

    @Query("SELECT t FROM RiskAppetiteThreshold t WHERE t.orgId = :orgId AND t.effectiveTo IS NULL")
    List<RiskAppetiteThreshold> findActiveByOrgId(@Param("orgId") UUID orgId);
}
