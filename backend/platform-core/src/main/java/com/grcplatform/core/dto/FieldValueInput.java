package com.grcplatform.core.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Value input for a single field when creating or updating a record. Exactly one of the typed value
 * fields should be non-null, matching fieldType.
 */
public record FieldValueInput(UUID fieldDefId, String fieldType, String textValue,
        BigDecimal numberValue, Instant dateValue, UUID referenceId, String referenceLabel) {
    public static FieldValueInput text(UUID fieldDefId, String value) {
        return new FieldValueInput(fieldDefId, "TEXT", value, null, null, null, null);
    }

    public static FieldValueInput number(UUID fieldDefId, BigDecimal value) {
        return new FieldValueInput(fieldDefId, "NUMBER", null, value, null, null, null);
    }

    public static FieldValueInput date(UUID fieldDefId, Instant value) {
        return new FieldValueInput(fieldDefId, "DATE", null, null, value, null, null);
    }

    public static FieldValueInput reference(UUID fieldDefId, UUID refId, String label) {
        return new FieldValueInput(fieldDefId, "REFERENCE", null, null, null, refId, label);
    }
}
