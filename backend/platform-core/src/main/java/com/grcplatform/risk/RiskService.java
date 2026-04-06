package com.grcplatform.risk;

import java.util.List;
import java.util.UUID;

public interface RiskService {

    /** Computes and stores the inherent risk score for the given risk record. */
    RiskScoreDto computeAndSaveScore(ComputeRiskScoreCommand cmd);

    /** Updates residual score after control association/effectiveness change. */
    RiskScoreDto updateResidualScore(UpdateResidualScoreCommand cmd);

    RiskScoreDto getScore(UUID riskRecordId);

    /** Batch load for GraphQL BatchMapping. */
    List<RiskScoreDto> getScoresForRecords(List<UUID> recordIds);

    /** Sets new risk appetite threshold (supersedes prior active row). */
    void setAppetiteThreshold(String category, int thresholdScore, String notes);
}
