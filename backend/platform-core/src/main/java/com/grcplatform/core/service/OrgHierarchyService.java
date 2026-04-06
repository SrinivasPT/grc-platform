package com.grcplatform.core.service;

import com.grcplatform.core.dto.CreateOrgUnitCommand;
import com.grcplatform.core.dto.OrgUnitDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrgHierarchyService {

    OrgUnitDto createUnit(CreateOrgUnitCommand cmd);

    OrgUnitDto updateUnit(UUID unitId, String name, String description, UUID managerId);

    OrgUnitDto moveUnit(UUID unitId, UUID newParentId);

    void deactivateUnit(UUID unitId, UUID reassignToUnitId);

    OrgUnitDto getUnit(UUID unitId);

    List<OrgUnitDto> getTree(UUID rootUnitId);

    List<OrgUnitDto> getAncestors(UUID unitId);

    /** Resolves the direct manager of a user based on their primary org unit. */
    Optional<UUID> findDirectManagerId(UUID userId);

    /** Returns all org unit IDs in the subtree rooted at the given unit (inclusive). */
    List<UUID> getSubtreeIds(UUID rootUnitId);
}
