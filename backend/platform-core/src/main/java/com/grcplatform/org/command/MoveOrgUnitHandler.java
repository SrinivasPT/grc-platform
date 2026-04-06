package com.grcplatform.org.command;

import java.util.List;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.exception.ValidationException;
import com.grcplatform.core.validation.Validator;
import com.grcplatform.org.MoveOrgUnitCommand;
import com.grcplatform.org.OrgUnitDto;
import com.grcplatform.org.OrgUnitRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MoveOrgUnitHandler {

    private final OrgUnitRepository orgUnitRepository;
    private final List<Validator<MoveOrgUnitCommand>> validators;

    public MoveOrgUnitHandler(OrgUnitRepository orgUnitRepository,
            List<Validator<MoveOrgUnitCommand>> validators) {
        this.orgUnitRepository = orgUnitRepository;
        this.validators = validators;
    }

    public OrgUnitDto handle(MoveOrgUnitCommand cmd) {
        validators.forEach(v -> v.validate(cmd));

        var ctx = SessionContextHolder.current();
        var orgId = ctx.orgId();

        var unit = orgUnitRepository.findByIdAndOrgId(cmd.unitId(), orgId)
                .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit", cmd.unitId()));

        String oldPath = unit.getPath();
        String newParentPath = "/";
        int newDepth = 0;

        if (cmd.newParentId() != null) {
            var newParent = orgUnitRepository.findByIdAndOrgId(cmd.newParentId(), orgId)
                    .orElseThrow(() -> new RecordNotFoundException("OrganizationUnit",
                            cmd.newParentId()));
            if (newParent.getPath().startsWith(oldPath)) {
                throw new ValidationException("newParentId",
                        "Cannot move a unit under its own descendant");
            }
            newParentPath = newParent.getPath();
            newDepth = newParent.getDepth() + 1;
        }

        String idSegment = cmd.unitId().toString().replace("-", "");
        String newPath = newParentPath + idSegment + "/";
        int depthDelta = newDepth - unit.getDepth();

        unit.setParentId(cmd.newParentId());
        unit.setPath(newPath);
        unit.setDepth(newDepth);
        orgUnitRepository.save(unit);

        orgUnitRepository.findByOrgIdAndPathStartingWith(orgId, oldPath).stream()
                .filter(u -> !u.getId().equals(cmd.unitId())).forEach(descendant -> {
                    String updatedPath = newPath + descendant.getPath().substring(oldPath.length());
                    descendant.setPath(updatedPath);
                    descendant.setDepth(descendant.getDepth() + depthDelta);
                    orgUnitRepository.save(descendant);
                });

        return unit.toDto();
    }
}
