package com.grcplatform.core.rule;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable input snapshot passed to all RuleEvaluator instances. Contains the current field values
 * and any already-computed values.
 */
public record EvaluationInput(UUID orgId, UUID recordId, UUID applicationId,
        Map<String, Object> currentFieldValues, Map<String, Object> computedValues) {
    public EvaluationInput {
        currentFieldValues = Map.copyOf(currentFieldValues);
        computedValues = Map.copyOf(computedValues);
    }

    public static EvaluationInput of(UUID orgId, UUID recordId, UUID applicationId,
            Map<String, Object> currentFieldValues, Map<String, Object> computedValues) {
        return new EvaluationInput(orgId, recordId, applicationId, currentFieldValues,
                computedValues);
    }
}
