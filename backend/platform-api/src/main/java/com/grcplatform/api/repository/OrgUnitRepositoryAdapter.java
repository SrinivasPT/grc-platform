package com.grcplatform.api.repository;

import com.grcplatform.org.OrganizationUnit;
import com.grcplatform.org.OrgUnitRepository;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class OrgUnitRepositoryAdapter implements OrgUnitRepository {

    private final SpringOrgUnitRepository jpa;

    public OrgUnitRepositoryAdapter(SpringOrgUnitRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public OrganizationUnit save(OrganizationUnit unit) {
        return jpa.save(unit);
    }

    @Override
    public Optional<OrganizationUnit> findByIdAndOrgId(UUID id, UUID orgId) {
        return jpa.findByIdAndOrgId(id, orgId);
    }

    @Override
    public List<OrganizationUnit> findByOrgId(UUID orgId) {
        return jpa.findByOrgId(orgId);
    }

    @Override
    public List<OrganizationUnit> findByOrgIdAndPathStartingWith(UUID orgId, String pathPrefix) {
        return jpa.findByOrgIdAndPathStartingWith(orgId, pathPrefix);
    }

    @Override
    public List<OrganizationUnit> findByParentIdAndOrgId(UUID parentId, UUID orgId) {
        return jpa.findByParentIdAndOrgId(parentId, orgId);
    }

    @Override
    public List<OrganizationUnit> findAncestors(UUID unitId, UUID orgId) {
        // The unit whose ancestors we need; find via id+orgId first to get its path
        Optional<OrganizationUnit> unitOpt = jpa.findByIdAndOrgId(unitId, orgId);
        if (unitOpt.isEmpty()) return List.of();

        String path = unitOpt.get().getPath();
        // Path: /seg1/seg2/seg3/ — split and strip first/last empty segments
        String[] segments = path.split("/");
        // Last segment is the unit itself — exclude it
        List<UUID> ancestorIds = Arrays.stream(segments).filter(s -> !s.isBlank()).map(s -> {
            // Re-insert hyphens: 32 hex chars → 8-4-4-4-12
            String hex = s;
            return UUID.fromString(
                    hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                            + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
        }).filter(id -> !id.equals(unitId)).toList();

        if (ancestorIds.isEmpty()) return List.of();
        return jpa.findByOrgIdAndIdIn(orgId, ancestorIds);
    }

    @Override
    public Optional<OrganizationUnit> findByOrgIdAndCode(UUID orgId, String code) {
        return jpa.findByOrgIdAndCode(orgId, code);
    }
}
