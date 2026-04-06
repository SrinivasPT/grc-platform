package com.grcplatform.api.graphql;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import com.grcplatform.control.ControlEffectivenessDto;
import com.grcplatform.control.ControlService;
import com.grcplatform.control.RecordTestResultCommand;
import com.grcplatform.core.dto.RecordDto;

@Controller
public class ControlResolver {

    private final ControlService controlService;

    public ControlResolver(ControlService controlService) {
        this.controlService = controlService;
    }

    @QueryMapping
    public ControlEffectivenessDto controlEffectiveness(@Argument UUID controlRecordId) {
        return controlService.getEffectiveness(controlRecordId).orElse(null);
    }

    @QueryMapping
    public List<UUID> controlsForRisk(@Argument UUID riskRecordId) {
        return controlService.getControlsForRisk(riskRecordId);
    }

    @MutationMapping
    public ControlEffectivenessDto recordControlTestResult(@Argument UUID controlRecordId,
            @Argument String testDate, @Argument String testResult, @Argument int exceptionsCount,
            @Argument String notes) {
        return controlService.recordTestResult(new RecordTestResultCommand(controlRecordId,
                LocalDate.parse(testDate), testResult, exceptionsCount, notes));
    }

    @MutationMapping
    public ControlEffectivenessDto recomputeControlEffectiveness(@Argument UUID controlRecordId) {
        return controlService.computeEffectivenessScore(controlRecordId);
    }

    /** BatchMapping: one query for all control records' effectiveness. */
    @BatchMapping(typeName = "GrcRecord", field = "controlEffectiveness")
    public Map<RecordDto, ControlEffectivenessDto> controlEffectivenessForRecords(
            List<RecordDto> records) {
        return records.stream().collect(Collectors.toMap(r -> r,
                r -> controlService.getEffectiveness(r.id()).orElse(null)));
    }
}
