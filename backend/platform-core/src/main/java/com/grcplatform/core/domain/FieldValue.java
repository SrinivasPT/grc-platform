package com.grcplatform.core.domain;

/**
 * Sealed interface for all field value variants.
 * Use switch expressions with pattern matching — no instanceof casts.
 * See ADR-006 and platform-core copilot-instructions.md.
 */
public sealed interface FieldValue
        permits FieldValue.TextValue,
                FieldValue.NumericValue,
                FieldValue.DateValue,
                FieldValue.BooleanValue,
                FieldValue.ReferenceValue,
                FieldValue.MultiSelectValue {

    record TextValue(String text) implements FieldValue {}
    record NumericValue(java.math.BigDecimal value) implements FieldValue {}
    record DateValue(java.time.LocalDate date) implements FieldValue {}
    record BooleanValue(boolean flag) implements FieldValue {}
    record ReferenceValue(java.util.UUID referencedId, String displayLabel) implements FieldValue {}
    record MultiSelectValue(java.util.List<String> values) implements FieldValue {
        public MultiSelectValue {
            values = java.util.List.copyOf(values);
        }
    }
}
