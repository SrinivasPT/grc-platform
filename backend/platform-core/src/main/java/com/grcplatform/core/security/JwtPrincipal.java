package com.grcplatform.core.security;

import java.util.List;
import java.util.UUID;

/**
 * Immutable value object representing the authenticated principal extracted from a validated JWT.
 * Constructed by JwtPrincipalConverter in platform-api; consumed by GraphQL resolvers and REST
 * controllers via @AuthenticationPrincipal.
 */
public record JwtPrincipal(UUID orgId, UUID userId, String username, List<String> roles,
        int roleVersion) {
    public static JwtPrincipal of(UUID orgId, UUID userId, String username, List<String> roles,
            int roleVersion) {
        return new JwtPrincipal(orgId, userId, username, List.copyOf(roles), roleVersion);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
