package com.grcplatform.core.dto;

import java.time.Instant;
import java.util.UUID;

public record PolicyAcknowledgmentDto(UUID id, UUID policyRecordId, UUID userId,
        Instant acknowledgedAt, String policyVersion, String method) {
}
