package com.grcplatform.core.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RiskScoreDto(UUID recordId, BigDecimal likelihoodScore, BigDecimal impactScore,
        BigDecimal inherentScore, String inherentRating, BigDecimal residualScore,
        String residualRating, String appetiteAlignment, Instant computedAt) {
}
