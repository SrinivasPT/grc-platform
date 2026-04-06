package com.grcplatform.control;

import com.grcplatform.control.ControlEffectiveness;
import java.util.Optional;
import java.util.UUID;

public interface ControlEffectivenessRepository {
    ControlEffectiveness save(ControlEffectiveness effectiveness);

    Optional<ControlEffectiveness> findByControlRecordIdAndOrgId(UUID controlRecordId, UUID orgId);
}
