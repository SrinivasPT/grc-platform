package com.grcplatform.api.repository;

import com.grcplatform.control.ControlEffectiveness;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringControlEffectivenessRepository extends JpaRepository<ControlEffectiveness, UUID> {

    Optional<ControlEffectiveness> findByControlRecordIdAndOrgId(UUID controlRecordId, UUID orgId);
}
