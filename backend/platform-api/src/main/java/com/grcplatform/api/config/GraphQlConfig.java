package com.grcplatform.api.config;

import org.springframework.context.annotation.Configuration;

/**
 * GraphQL runtime configuration. - UUIDs are serialised as the built-in ID scalar (String under the
 * hood) — no extra dependency. - Introspection is disabled in production via application.yml.
 */
@Configuration
public class GraphQlConfig {

    /**
     * Maximum allowed GraphQL query nesting depth (enforced by instrumentation in future phases).
     */
    static final int MAX_QUERY_DEPTH = 8;
}
