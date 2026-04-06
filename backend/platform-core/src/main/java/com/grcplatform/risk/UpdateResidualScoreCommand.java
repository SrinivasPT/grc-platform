package com.grcplatform.risk;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateResidualScoreCommand(UUID riskRecordId, BigDecimal residualLikelihood,
        BigDecimal residualImpact) {
}
