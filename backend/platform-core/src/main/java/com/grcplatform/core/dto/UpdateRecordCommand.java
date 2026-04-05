package com.grcplatform.core.dto;

import java.util.List;
import java.util.UUID;

/**
 * Command to update the mutable fields of an existing GRC record.
 */
public record UpdateRecordCommand(UUID recordId, String displayName,
        List<FieldValueInput> fieldValues) {
    public UpdateRecordCommand {
        fieldValues = fieldValues != null ? List.copyOf(fieldValues) : List.of();
    }
}
