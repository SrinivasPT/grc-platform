package com.grcplatform.api.graphql.dto;

import java.util.List;
import java.util.UUID;
import com.grcplatform.core.domain.FieldValueDate;
import com.grcplatform.core.domain.FieldValueNumber;
import com.grcplatform.core.domain.FieldValueReference;
import com.grcplatform.core.domain.FieldValueText;

/**
 * GraphQL output DTO for a field value entry. Combines all field value types into a flat projection
 * that the GraphQL schema's FieldValue type maps to.
 */
public record FieldValueProjection(UUID fieldDefId, String fieldType, String textValue,
        String numberValue, String dateValue, List<UUID> referenceIds) {
    public static FieldValueProjection fromText(FieldValueText v) {
        return new FieldValueProjection(v.getFieldDefId(), "TEXT", v.getValue(), null, null,
                List.of());
    }

    public static FieldValueProjection fromNumber(FieldValueNumber v) {
        var numStr = v.getValue() != null ? v.getValue().toPlainString() : null;
        return new FieldValueProjection(v.getFieldDefId(), "NUMBER", null, numStr, null, List.of());
    }

    public static FieldValueProjection fromDate(FieldValueDate v) {
        var dateStr = v.getValue() != null ? v.getValue().toString() : null;
        return new FieldValueProjection(v.getFieldDefId(), "DATE", null, null, dateStr, List.of());
    }

    public static FieldValueProjection fromReference(FieldValueReference v) {
        return new FieldValueProjection(v.getFieldDefId(), "REFERENCE", null, null, null,
                List.of(v.getRefId()));
    }
}
