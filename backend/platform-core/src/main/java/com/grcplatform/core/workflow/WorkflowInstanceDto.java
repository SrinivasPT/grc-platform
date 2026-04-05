package com.grcplatform.core.workflow;

import java.time.Instant;
import java.util.UUID;

/** Result DTO returned after a successful transition. */
public record WorkflowInstanceDto(UUID id, UUID recordId, UUID definitionId, String currentState,
        String status, Instant enteredStateAt, int version) {
}
