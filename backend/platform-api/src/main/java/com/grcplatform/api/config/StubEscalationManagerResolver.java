package com.grcplatform.api.config;

import java.util.UUID;
import com.grcplatform.workflow.EscalationManagerResolver;

/**
 * Stub implementation of EscalationManagerResolver. Superseded by OrgHierarchyManagerResolver; kept
 * for reference only — not registered as a Spring bean.
 */
class StubEscalationManagerResolver implements EscalationManagerResolver {

    @Override
    public UUID resolveManager(UUID orgId, UUID userId) {
        return null;
    }
}
