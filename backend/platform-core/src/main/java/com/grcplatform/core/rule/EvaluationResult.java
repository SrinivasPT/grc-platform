package com.grcplatform.core.rule;

import java.util.Map;

/**
 * Sealed result hierarchy for the three rule evaluation contexts. Use switch pattern matching to
 * dispatch on the result type.
 */
public sealed interface EvaluationResult permits EvaluationResult.ComputeResult,
        EvaluationResult.ValidateResult, EvaluationResult.TriggerResult {

    /**
     * Result of a COMPUTE rule. The computed value to be stored as the target field.
     */
    record ComputeResult(Object value) implements EvaluationResult {
    }

    /**
     * Result of a VALIDATE rule. If not valid, fieldKey and message identify the problem.
     */
    record ValidateResult(boolean valid, String fieldKey, String message)
            implements EvaluationResult {
        public static ValidateResult pass() {
            return new ValidateResult(true, null, null);
        }

        public static ValidateResult fail(String fieldKey, String message) {
            return new ValidateResult(false, fieldKey, message);
        }
    }

    /**
     * Result of a TRIGGER rule. Indicates whether the post-save event should fire.
     */
    record TriggerResult(boolean triggered) implements EvaluationResult {
        public static TriggerResult yes() {
            return new TriggerResult(true);
        }

        public static TriggerResult no() {
            return new TriggerResult(false);
        }
    }
}
