package com.grcplatform.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrgHierarchyService {

    OrgUnitDto createUnit(CreateOrgUnitCommand cmd);

    OrgUnitDto updateUnit(UUID unitId, UpdateOrgUnitCommand cmd);

    OrgUnitDto moveUnit(MoveOrgUnitCommand cmd);

    void deactivateUnit(UUID unitId, UUID reassignToUnitId);

    OrgUnitDto getUnit(UUID unitId);

    List<OrgUnitDto> getTree(UUID rootUnitId);

    List<OrgUnitDto> getAncestors(UUID unitId);

    /** Resolves the direct manager of a user based on their primary org unit. */
    Optional<UUID> findDirectManagerId(UUID userId);

    /** Returns all org unit IDs in the subtree rooted at the given unit (inclusive). */
    List<UUID> getSubtreeIds(UUID rootUnitId);
}
