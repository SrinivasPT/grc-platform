package com.grcplatform.core.rule;

/**
 * The three execution contexts for the rule engine.
 * A rule definition targets exactly one context — never mixed.
 * See ADR-006.
 */
public enum RuleContext {
    /**
     * Derives a field value from other fields.
     * Evaluated before the field is stored. Result replaces the field value.
     */
    COMPUTE,

    /**
     * Enforces data integrity rules before save.
     * Failure throws ValidationException with field-level errors.
     */
    VALIDATE,

    /**
     * Post-save condition. If matched, publishes RuleTriggerEvent to the outbox.
     * Never calls notification or integration services directly.
     */
    TRIGGER
}
