package com.grcplatform.workflow;

import java.util.UUID;

/**
 * Resolves the manager of a given user within an org. Implementation lives in platform-api (JPA
 * query). Interface here keeps the workflow module independent of Spring Data.
 */
public interface EscalationManagerResolver {

    /**
     * Returns the UUID of the manager of the given userId within the org, or null if no manager is
     * configured (e.g., the user is at the org root).
     */
    UUID resolveManager(UUID orgId, UUID userId);
}
