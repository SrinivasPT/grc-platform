package com.grcplatform.api.config;

import java.util.UUID;
import com.grcplatform.org.OrgHierarchyService;
import com.grcplatform.workflow.EscalationManagerResolver;

/**
 * Resolves escalation managers via the Org Hierarchy module. Replaces StubEscalationManagerResolver
 * now that Module 26 is implemented.
 */
public class OrgHierarchyManagerResolver implements EscalationManagerResolver {

    private final OrgHierarchyService orgHierarchyService;

    public OrgHierarchyManagerResolver(OrgHierarchyService orgHierarchyService) {
        this.orgHierarchyService = orgHierarchyService;
    }

    @Override
    public UUID resolveManager(UUID orgId, UUID userId) {
        return orgHierarchyService.findDirectManagerId(userId).orElse(null);
    }
}
