package com.grcplatform.risk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskAppetiteThresholdRepository {

    RiskAppetiteThreshold save(RiskAppetiteThreshold threshold);

    /** Returns the currently active threshold (effectiveTo IS NULL) for the given category. */
    Optional<RiskAppetiteThreshold> findActiveByOrgIdAndCategory(UUID orgId, String category);

    /** Returns all active thresholds for an org (for display/config). */
    List<RiskAppetiteThreshold> findActiveByOrgId(UUID orgId);
}
