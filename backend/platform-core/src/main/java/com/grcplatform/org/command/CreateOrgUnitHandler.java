package com.grcplatform.org.command;

import java.util.List;
import java.util.UUID;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.exception.ValidationException;
import com.grcplatform.core.validation.Validator;
import com.grcplatform.org.CreateOrgUnitCommand;
import com.grcplatform.org.OrganizationUnit;
import com.grcplatform.org.OrgUnitDto;
import com.grcplatform.org.OrgUnitRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateOrgUnitHandler {

    private final OrgUnitRepository orgUnitRepository;
    private final List<Validator<CreateOrgUnitCommand>> validators;

    public CreateOrgUnitHandler(OrgUnitRepository orgUnitRepository,
            List<Validator<CreateOrgUnitCommand>> validators) {
        this.orgUnitRepository = orgUnitRepository;
        this.validators = validators;
    }

    public OrgUnitDto handle(CreateOrgUnitCommand cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new ValidationException("name", "Name is required");
        }
        if (cmd.unitType() == null || cmd.unitType().isBlank()) {
            throw new ValidationException("unitType", "Unit type is required");
        }
        validators.forEach(v -> v.validate(cmd));

        var ctx = SessionContextHolder.current();
        var orgId = ctx.orgId();
        UUID parentId = cmd.parentId();
        String parentPath = "/";
        int depth = 0;

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
        return orgUnitRepository.save(unit).toDto();
    }
}
