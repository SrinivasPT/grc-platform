package com.grcplatform.core.service;

import com.grcplatform.core.dto.RiskScoreDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RiskService {

    /** Computes and stores the inherent risk score for the given risk record. */
    RiskScoreDto computeAndSaveScore(UUID riskRecordId, BigDecimal likelihood, BigDecimal impact);

    /** Updates residual score after control association/effectiveness change. */
    RiskScoreDto updateResidualScore(UUID riskRecordId, BigDecimal residualLikelihood,
            BigDecimal residualImpact);

    RiskScoreDto getScore(UUID riskRecordId);

    /** Batch load for GraphQL BatchMapping. */
    List<RiskScoreDto> getScoresForRecords(List<UUID> recordIds);

    /** Sets new risk appetite threshold (supersedes prior active row). */
    void setAppetiteThreshold(String category, int thresholdScore, String notes);
}
