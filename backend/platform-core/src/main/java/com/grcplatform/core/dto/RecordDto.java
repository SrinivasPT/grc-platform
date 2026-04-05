package com.grcplatform.core.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Full record projection returned from RecordService.get() and mutations.
 */
public record RecordDto(UUID id, UUID orgId, UUID applicationId, String displayName,
        String displayNumber, String status, String workflowState,
        Map<String, Object> computedValues, Instant createdAt, Instant updatedAt, long version) {
}
