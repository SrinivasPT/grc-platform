package com.grcplatform.api.graphql.dto;

import java.util.UUID;

public record TransitionWorkflowInput(UUID instanceId, String transitionKey, String comment,
        String idempotencyKey) {
}
