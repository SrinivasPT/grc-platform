package com.grcplatform.api.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import com.grcplatform.control.ControlEffectiveness;
import com.grcplatform.control.ControlEffectivenessRepository;

@Repository
public class ControlEffectivenessRepositoryAdapter implements ControlEffectivenessRepository {

    private final SpringControlEffectivenessRepository spring;

    public ControlEffectivenessRepositoryAdapter(SpringControlEffectivenessRepository spring) {
        this.spring = spring;
    }

    @Override
    public ControlEffectiveness save(ControlEffectiveness effectiveness) {
        return spring.save(effectiveness);
    }

    @Override
    public Optional<ControlEffectiveness> findByControlRecordIdAndOrgId(UUID controlRecordId,
            UUID orgId) {
        return spring.findByControlRecordIdAndOrgId(controlRecordId, orgId);
    }
}
