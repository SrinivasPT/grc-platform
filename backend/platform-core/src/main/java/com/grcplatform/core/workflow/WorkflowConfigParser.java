package com.grcplatform.core.workflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.grcplatform.core.exception.ValidationException;

/**
 * Parses workflow_definitions.config JSON into a typed WorkflowConfig.
 *
 * Security: Jackson is configured with polymorphic typing DISABLED (OWASP A08). No @JsonTypeInfo or
 * type coercion; strict model classes only.
 */
public class WorkflowConfigParser {

    private static final int MAX_STATES = 50;
    private static final int MAX_TRANSITIONS = 100;

    private final ObjectMapper mapper;

    public WorkflowConfigParser() {
        this.mapper = JsonMapper.builder().disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    public WorkflowConfig parse(String json) {
        if (json == null || json.isBlank()) {
            throw new ValidationException("config", "workflow config must not be blank");
        }
        WorkflowConfig config;
        try {
            config = mapper.readValue(json, WorkflowConfig.class);
        } catch (Exception e) {
            throw new ValidationException("config",
                    "workflow config is not valid JSON: " + e.getMessage());
        }
        validate(config);
        return config;
    }

    private void validate(WorkflowConfig config) {
        if (config.initialState() == null || config.initialState().isBlank()) {
            throw new ValidationException("initialState",
                    "workflow config must specify initialState");
        }
        if (config.states() == null || config.states().isEmpty()) {
            throw new ValidationException("states",
                    "workflow config must define at least one state");
        }
        if (config.states().size() > MAX_STATES) {
            throw new ValidationException("states",
                    "workflow config exceeds maximum of " + MAX_STATES + " states");
        }
        if (config.transitions() != null && config.transitions().size() > MAX_TRANSITIONS) {
            throw new ValidationException("transitions",
                    "workflow config exceeds maximum of " + MAX_TRANSITIONS + " transitions");
        }
        boolean initialFound =
                config.states().stream().anyMatch(s -> s.key().equals(config.initialState()));
        if (!initialFound) {
            throw new ValidationException("initialState",
                    "initialState '" + config.initialState() + "' not found in states list");
        }
    }
}
