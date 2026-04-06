package com.grcplatform.core.repository;

import com.grcplatform.core.domain.ControlEffectiveness;
import java.util.Optional;
import java.util.UUID;

public interface ControlEffectivenessRepository {
    ControlEffectiveness save(ControlEffectiveness effectiveness);

    Optional<ControlEffectiveness> findByControlRecordIdAndOrgId(UUID controlRecordId, UUID orgId);
}
