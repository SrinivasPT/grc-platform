package com.grcplatform.core.workflow;

import java.util.UUID;

/** Command to execute a workflow transition. */
public record TransitionCommand(UUID instanceId, String transitionKey, UUID actorId,
        String comment) {
}
