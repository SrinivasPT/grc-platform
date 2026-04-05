package com.grcplatform.core.dto;

import java.util.List;
import java.util.UUID;

/**
 * Command to create a new GRC record within an application. idempotencyKey is required on all
 * state-changing API calls (see global copilot-instructions §2).
 */
public record CreateRecordCommand(UUID applicationId, String displayName,
        List<FieldValueInput> fieldValues, String idempotencyKey) {
    public CreateRecordCommand {
        fieldValues = fieldValues != null ? List.copyOf(fieldValues) : List.of();
    }
}
