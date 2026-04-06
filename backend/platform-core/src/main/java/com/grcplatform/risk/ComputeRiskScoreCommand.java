package com.grcplatform.risk;

import java.math.BigDecimal;
import java.util.UUID;

public record ComputeRiskScoreCommand(UUID riskRecordId, BigDecimal likelihood, BigDecimal impact) {
}
