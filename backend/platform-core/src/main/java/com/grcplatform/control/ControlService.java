package com.grcplatform.control;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.grcplatform.control.ControlEffectivenessDto;
import com.grcplatform.control.RecordTestResultCommand;

public interface ControlService {

    /** Records a test result and recomputes the control's effectiveness score. */
    ControlEffectivenessDto recordTestResult(RecordTestResultCommand cmd);

    /** Recomputes and caches the effectiveness score for the given control. */
    ControlEffectivenessDto computeEffectivenessScore(UUID controlRecordId);

    /** Returns IDs of controls linked to the given risk via MITIGATES relations. */
    List<UUID> getControlsForRisk(UUID riskRecordId);

    /** Returns the cached effectiveness score for a control, if present. */
    Optional<ControlEffectivenessDto> getEffectiveness(UUID controlRecordId);
}
