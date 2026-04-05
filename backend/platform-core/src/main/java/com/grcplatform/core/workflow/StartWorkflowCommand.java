package com.grcplatform.core.workflow;

import java.util.UUID;

/** Command to start a new workflow instance for a record. */
public record StartWorkflowCommand(
        UUID recordId,
        UUID applicationId,
        UUID actorId
) {}
