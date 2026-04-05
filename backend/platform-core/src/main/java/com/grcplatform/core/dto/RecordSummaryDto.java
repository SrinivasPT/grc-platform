package com.grcplatform.core.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight record projection for list views and BatchMapping responses.
 */
public record RecordSummaryDto(UUID id, String displayName, String displayNumber, String status,
        Instant updatedAt) {
}
