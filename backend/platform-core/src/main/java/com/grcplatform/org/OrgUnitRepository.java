package com.grcplatform.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrgUnitRepository {

    OrganizationUnit save(OrganizationUnit unit);

    Optional<OrganizationUnit> findByIdAndOrgId(UUID id, UUID orgId);

    List<OrganizationUnit> findByOrgId(UUID orgId);

    /** Subtree query: all units whose path starts with the given path prefix. */
    List<OrganizationUnit> findByOrgIdAndPathStartingWith(UUID orgId, String pathPrefix);

    List<OrganizationUnit> findByParentIdAndOrgId(UUID parentId, UUID orgId);

    /** Ancestors: resolves IDs embedded in the path and returns them ordered root-first. */
    List<OrganizationUnit> findAncestors(UUID unitId, UUID orgId);

    Optional<OrganizationUnit> findByOrgIdAndCode(UUID orgId, String code);
}
