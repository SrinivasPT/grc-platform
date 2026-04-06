package com.grcplatform.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.org.command.CreateOrgUnitHandler;
import com.grcplatform.org.command.MoveOrgUnitHandler;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrgHierarchyServiceImpl implements OrgHierarchyService {

    private final OrgUnitRepository orgUnitRepository;
    private final UserOrgUnitRepository userOrgUnitRepository;
    private final CreateOrgUnitHandler createOrgUnitHandler;
    private final MoveOrgUnitHandler moveOrgUnitHandler;

    public OrgHierarchyServiceImpl(OrgUnitRepository orgUnitRepository,
            UserOrgUnitRepository userOrgUnitRepository, CreateOrgUnitHandler createOrgUnitHandler,
            MoveOrgUnitHandler moveOrgUnitHandler) {
        this.orgUnitRepository = orgUnitRepository;
        this.userOrgUnitRepository = userOrgUnitRepository;
        this.createOrgUnitHandler = createOrgUnitHandler;
        this.moveOrgUnitHandler = moveOrgUnitHandler;
    }

    @Override
    @Transactional
    public OrgUnitDto createUnit(CreateOrgUnitCommand cmd) {
        return createOrgUnitHandler.handle(cmd);
    }

    @Override
    @Transactional
    public OrgUnitDto updateUnit(UUID unitId, UpdateOrgUnitCommand cmd) {
        var ctx = SessionContextHolder.current();
        var unit = orgUnitRepository.findByIdAndOrgId(unitId, ctx.orgId())
                .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit", unitId));

        if (cmd.name() != null && !cmd.name().isBlank()) unit.setName(cmd.name());
        if (cmd.description() != null) unit.setDescription(cmd.description());
        if (cmd.managerId() != null) unit.setManagerId(cmd.managerId());

        return orgUnitRepository.save(unit).toDto();
    }

    @Override
    @Transactional
    public OrgUnitDto moveUnit(MoveOrgUnitCommand cmd) {
        return moveOrgUnitHandler.handle(cmd);
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
        return orgUnitRepository.findByIdAndOrgId(unitId, ctx.orgId())
                .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit", unitId)).toDto();
    }

    @Override
    public List<OrgUnitDto> getTree(UUID rootUnitId) {
        var ctx = SessionContextHolder.current();
        var orgId = ctx.orgId();

        if (rootUnitId == null) {
            return orgUnitRepository.findByOrgId(orgId).stream().map(OrganizationUnit::toDto)
                    .toList();
        }
        var root = orgUnitRepository.findByIdAndOrgId(rootUnitId, orgId)
                .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit", rootUnitId));
        return orgUnitRepository.findByOrgIdAndPathStartingWith(orgId, root.getPath()).stream()
                .map(OrganizationUnit::toDto).toList();
    }

    @Override
    public List<OrgUnitDto> getAncestors(UUID unitId) {
        var ctx = SessionContextHolder.current();
        return orgUnitRepository.findAncestors(unitId, ctx.orgId()).stream()
                .map(OrganizationUnit::toDto).toList();
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
}
