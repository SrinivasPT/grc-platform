package com.grcplatform.api.repository;

import com.grcplatform.org.OrganizationUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringOrgUnitRepository extends JpaRepository<OrganizationUnit, UUID> {

    Optional<OrganizationUnit> findByIdAndOrgId(UUID id, UUID orgId);

    List<OrganizationUnit> findByOrgId(UUID orgId);

    List<OrganizationUnit> findByOrgIdAndPathStartingWith(UUID orgId, String pathPrefix);

    List<OrganizationUnit> findByParentIdAndOrgId(UUID parentId, UUID orgId);

    Optional<OrganizationUnit> findByOrgIdAndCode(UUID orgId, String code);

    /**
     * Ancestors: extract IDs from path segments (excluding the unit itself), then fetch units. Path
     * format: /seg1/seg2/seg3/ — each segment is a UUID without hyphens.
     */
    @Query("""
            SELECT u FROM OrganizationUnit u
            WHERE u.orgId = :orgId
            AND u.id IN :ids
            ORDER BY u.depth ASC
            """)
    List<OrganizationUnit> findByOrgIdAndIdIn(@Param("orgId") UUID orgId,
            @Param("ids") List<UUID> ids);
}
