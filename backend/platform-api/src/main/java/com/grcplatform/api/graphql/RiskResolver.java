package com.grcplatform.api.graphql;

import com.grcplatform.risk.ComputeRiskScoreCommand;
import com.grcplatform.risk.RiskScoreDto;
import com.grcplatform.risk.UpdateResidualScoreCommand;
import com.grcplatform.core.dto.RecordDto;
import com.grcplatform.risk.RiskService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class RiskResolver {

    private final RiskService riskService;

    public RiskResolver(RiskService riskService) {
        this.riskService = riskService;
    }

    @QueryMapping
    public RiskScoreDto riskScore(@Argument UUID riskRecordId) {
        return riskService.getScore(riskRecordId);
    }

    @MutationMapping
    public RiskScoreDto computeRiskScore(@Argument UUID riskRecordId,
            @Argument BigDecimal likelihood, @Argument BigDecimal impact) {
        return riskService
                .computeAndSaveScore(new ComputeRiskScoreCommand(riskRecordId, likelihood, impact));
    }

    @MutationMapping
    public RiskScoreDto updateResidualRiskScore(@Argument UUID riskRecordId,
            @Argument BigDecimal residualLikelihood, @Argument BigDecimal residualImpact) {
        return riskService.updateResidualScore(
                new UpdateResidualScoreCommand(riskRecordId, residualLikelihood, residualImpact));
    }

    @MutationMapping
    public boolean setRiskAppetite(@Argument String category, @Argument int thresholdScore,
            @Argument String notes) {
        riskService.setAppetiteThreshold(category, thresholdScore, notes);
        return true;
    }

    /** BatchMapping: one query for all risk records' scores. */
    @BatchMapping(typeName = "GrcRecord", field = "riskScore")
    public Map<RecordDto, RiskScoreDto> riskScores(List<RecordDto> records) {
        var ids = records.stream().map(RecordDto::id).toList();
        var scores = riskService.getScoresForRecords(ids).stream()
                .collect(Collectors.toMap(RiskScoreDto::recordId, s -> s));
        return records.stream().collect(Collectors.toMap(r -> r, r -> scores.get(r.id())));
    }
}
