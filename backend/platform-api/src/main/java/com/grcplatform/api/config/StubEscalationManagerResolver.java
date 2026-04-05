package com.grcplatform.api.config;

import com.grcplatform.workflow.EscalationManagerResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub implementation of EscalationManagerResolver.
 * Returns null (no manager) for all users until the org-hierarchy module is built in Phase 4.
 * At that point this bean is replaced by OrgHierarchyManagerResolver.
 */
@Component
public class StubEscalationManagerResolver implements EscalationManagerResolver {

    @Override
    public UUID resolveManager(UUID orgId, UUID userId) {
        return null;
    }
}
