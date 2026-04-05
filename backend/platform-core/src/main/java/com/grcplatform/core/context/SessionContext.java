package com.grcplatform.core.context;

import java.util.List;
import java.util.UUID;

/**
 * Immutable request context propagated via ScopedValue.
 * Never use ThreadLocal — see ADR-008.
 */
public record SessionContext(
        UUID orgId,
        UUID userId,
        String username,
        List<String> roles,
        int roleVersion
) {
    public static SessionContext of(UUID orgId, UUID userId, String username,
                                    List<String> roles, int roleVersion) {
        return new SessionContext(orgId, userId, username,
                List.copyOf(roles), roleVersion);
    }
}
