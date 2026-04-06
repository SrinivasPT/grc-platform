package com.grcplatform.control;

import java.time.Instant;
import java.util.UUID;

public record ControlEffectivenessDto(UUID controlRecordId, int effectivenessScore,
        String effectivenessRating, int testCount12m, Instant computedAt) {
}
