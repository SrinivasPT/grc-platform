package com.grcplatform.api.graphql.dto;

import com.grcplatform.core.dto.FieldValueInput;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL input type for the createRecord mutation. Maps one-to-one to CreateRecordCommand (the
 * service never sees GraphQL types — only core DTOs).
 */
public record CreateRecordInput(UUID applicationId, String displayName,
        List<FieldValueInput> fieldValues, String idempotencyKey) {
    public CreateRecordInput {
        fieldValues = fieldValues != null ? List.copyOf(fieldValues) : List.of();
    }
}
