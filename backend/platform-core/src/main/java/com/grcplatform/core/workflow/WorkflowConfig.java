package com.grcplatform.core.workflow;

import java.util.List;
import java.util.Map;

/**
 * Immutable parsed representation of a workflow_definitions.config JSON.
 * Uses only plain record types — no @JsonTypeInfo, no polymorphic typing (OWASP A08).
 */
public record WorkflowConfig(
        String id,
        String name,
        String appKey,
        int version,
        String initialState,
        List<StateConfig> states,
        List<TransitionConfig> transitions
) {

    public record StateConfig(
            String key,
            String label,
            boolean terminal,
            String color,
            Integer slaTtlHours,
            String slaAction,
            String slaEscalateToRole
    ) {}

    public record ActorConfig(
            String type,       // "role" | "field_reference" | "system"
            String role,
            String fieldKey
    ) {}

    public record ConditionConfig(
            Map<String, Object> ruleDsl,
            String error
    ) {}

    public record ActionConfig(
            String type,       // "create_task" | "notify" | "auto_transition"
            String role,
            String label,
            String template,
            List<Map<String, Object>> recipients
    ) {}

    public record TransitionConfig(
            String key,
            String label,
            List<String> fromStates,
            String toState,
            ActorConfig actors,
            List<ActorConfig> parallelActors,
            String completionMode,        // "all" | "any"
            boolean requireComment,
            List<ConditionConfig> conditions,
            List<ActionConfig> onEnterActions
    ) {}

    /** Returns the StateConfig for the given key, or null if not found. */
    public StateConfig stateByKey(String key) {
        return states.stream().filter(s -> s.key().equals(key)).findFirst().orElse(null);
    }

    /** Returns all TransitionConfigs whose fromStates contains the given state key. */
    public List<TransitionConfig> transitionsFrom(String stateKey) {
        return transitions.stream()
                .filter(t -> t.fromStates().contains(stateKey))
                .toList();
    }

    /** Returns the TransitionConfig matching the given transition key, or null. */
    public TransitionConfig transitionByKey(String key) {
        return transitions.stream().filter(t -> t.key().equals(key)).findFirst().orElse(null);
    }
}
