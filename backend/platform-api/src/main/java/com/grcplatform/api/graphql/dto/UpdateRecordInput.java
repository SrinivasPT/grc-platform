package com.grcplatform.api.graphql.dto;

import com.grcplatform.core.dto.FieldValueInput;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL input type for the updateRecord mutation.
 */
public record UpdateRecordInput(UUID recordId, String displayName,
        List<FieldValueInput> fieldValues) {
    public UpdateRecordInput {
        fieldValues = fieldValues != null ? List.copyOf(fieldValues) : List.of();
    }
}
