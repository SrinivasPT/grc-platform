package com.grcplatform.core.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.domain.OrganizationUnit;
import com.grcplatform.core.dto.CreateOrgUnitCommand;
import com.grcplatform.core.dto.OrgUnitDto;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.exception.ValidationException;
import com.grcplatform.core.repository.OrgUnitRepository;
import com.grcplatform.core.repository.UserOrgUnitRepository;
import jakarta.transaction.Transactional;

public class OrgHierarchyServiceImpl implements OrgHierarchyService {

    private final OrgUnitRepository orgUnitRepository;
    private final UserOrgUnitRepository userOrgUnitRepository;

    public OrgHierarchyServiceImpl(OrgUnitRepository orgUnitRepository,
            UserOrgUnitRepository userOrgUnitRepository) {
        this.orgUnitRepository = orgUnitRepository;
        this.userOrgUnitRepository = userOrgUnitRepository;
    }

    @Override
    @Transactional
    public OrgUnitDto createUnit(CreateOrgUnitCommand cmd) {
        var ctx = SessionContextHolder.current();
        var orgId = ctx.orgId();

        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new ValidationException("name", "Name is required");
        }
        if (cmd.unitType() == null || cmd.unitType().isBlank()) {
            throw new ValidationException("unitType", "Unit type is required");
        }

        String parentPath = "/";
        int depth = 0;
        UUID parentId = cmd.parentId();

        if (parentId != null) {
            var parent = orgUnitRepository.findByIdAndOrgId(parentId, orgId)
                    .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit", parentId));
            parentPath = parent.getPath();
            depth = parent.getDepth() + 1;
        }

        var newId = UUID.randomUUID();
        String path = parentPath + newId.toString().replace("-", "") + "/";

        var unit = OrganizationUnit.create(orgId, parentId, path, depth, cmd.unitType(), cmd.code(),
                cmd.name(), cmd.description(), cmd.managerId());
        unit.setId(newId);
        unit.setDisplayOrder(cmd.displayOrder());

        var saved = orgUnitRepository.save(unit);
        return toDto(saved);
    }

    @Override
    @Transactional
    public OrgUnitDto updateUnit(UUID unitId, String name, String description, UUID managerId) {
        var ctx = SessionContextHolder.current();
        var unit = orgUnitRepository.findByIdAndOrgId(unitId, ctx.orgId())
                .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit", unitId));

        if (name != null && !name.isBlank()) unit.setName(name);
        if (description != null) unit.setDescription(description);
        if (managerId != null) unit.setManagerId(managerId);

        return toDto(orgUnitRepository.save(unit));
    }

    @Override
    @Transactional
    public OrgUnitDto moveUnit(UUID unitId, UUID newParentId) {
        var ctx = SessionContextHolder.current();
        var orgId = ctx.orgId();

        var unit = orgUnitRepository.findByIdAndOrgId(unitId, orgId)
                .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit", unitId));

        String oldPath = unit.getPath();

        String newParentPath = "/";
        int newDepth = 0;

        if (newParentId != null) {
            var newParent = orgUnitRepository.findByIdAndOrgId(newParentId, orgId).orElseThrow(
                    () -> new RecordNotFoundException("OrganizationUnit", newParentId));
            // Prevent moving a unit to its own descendant
            if (newParent.getPath().startsWith(oldPath)) {
                throw new ValidationException("newParentId",
                        "Cannot move a unit under its own descendant");
            }
            newParentPath = newParent.getPath();
            newDepth = newParent.getDepth() + 1;
        }

        String idSegment = unitId.toString().replace("-", "");
        String newPath = newParentPath + idSegment + "/";
        int depthDelta = newDepth - unit.getDepth();

        unit.setParentId(newParentId);
        unit.setPath(newPath);
        unit.setDepth(newDepth);
        orgUnitRepository.save(unit);

        // Update all descendants
        orgUnitRepository.findByOrgIdAndPathStartingWith(orgId, oldPath + "").stream()
                .filter(u -> !u.getId().equals(unitId)).forEach(descendant -> {
                    String updatedPath = newPath + descendant.getPath().substring(oldPath.length());
                    descendant.setPath(updatedPath);
                    descendant.setDepth(descendant.getDepth() + depthDelta);
                    orgUnitRepository.save(descendant);
                });

        return toDto(unit);
    }

    @Override
    @Transactional
    public void deactivateUnit(UUID unitId, UUID reassignToUnitId) {
        var ctx = SessionContextHolder.current();
        var unit = orgUnitRepository.findByIdAndOrgId(unitId, ctx.orgId())
                .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit", unitId));
        unit.setActive(false);
        orgUnitRepository.save(unit);
    }

    @Override
    public OrgUnitDto getUnit(UUID unitId) {
        var ctx = SessionContextHolder.current();
        return toDto(orgUnitRepository.findByIdAndOrgId(unitId, ctx.orgId())
                .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit", unitId)));
    }

    @Override
    public List<OrgUnitDto> getTree(UUID rootUnitId) {
        var ctx = SessionContextHolder.current();
        var orgId = ctx.orgId();

        if (rootUnitId == null) {
            return orgUnitRepository.findByOrgId(orgId).stream().map(this::toDto).toList();
        }
        var root = orgUnitRepository.findByIdAndOrgId(rootUnitId, orgId)
                .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit", rootUnitId));
        return orgUnitRepository.findByOrgIdAndPathStartingWith(orgId, root.getPath()).stream()
                .map(this::toDto).toList();
    }

    @Override
    public List<OrgUnitDto> getAncestors(UUID unitId) {
        var ctx = SessionContextHolder.current();
        return orgUnitRepository.findAncestors(unitId, ctx.orgId()).stream().map(this::toDto)
                .toList();
    }

    @Override
    public Optional<UUID> findDirectManagerId(UUID userId) {
        return userOrgUnitRepository.findPrimaryByUserId(userId)
                .map(uou -> orgUnitRepository
                        .findByIdAndOrgId(uou.getId().getOrgUnitId(),
                                SessionContextHolder.current().orgId())
                        .map(OrganizationUnit::getManagerId).orElse(null));
    }

    @Override
    public List<UUID> getSubtreeIds(UUID rootUnitId) {
        var ctx = SessionContextHolder.current();
        var root = orgUnitRepository.findByIdAndOrgId(rootUnitId, ctx.orgId())
                .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit", rootUnitId));
        return orgUnitRepository.findByOrgIdAndPathStartingWith(ctx.orgId(), root.getPath())
                .stream().map(OrganizationUnit::getId).toList();
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private OrgUnitDto toDto(OrganizationUnit u) {
        return new OrgUnitDto(u.getId(), u.getOrgId(), u.getParentId(), u.getPath(), u.getDepth(),
                u.getUnitType(), u.getCode(), u.getName(), u.getDescription(), u.getManagerId(),
                u.getDisplayOrder(), u.isActive());
    }
}
