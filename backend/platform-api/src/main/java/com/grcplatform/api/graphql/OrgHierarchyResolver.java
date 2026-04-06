package com.grcplatform.api.graphql;

import com.grcplatform.org.CreateOrgUnitCommand;
import com.grcplatform.org.MoveOrgUnitCommand;
import com.grcplatform.org.OrgUnitDto;
import com.grcplatform.org.OrgHierarchyService;
import com.grcplatform.org.UpdateOrgUnitCommand;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GraphQL resolver for Org Hierarchy queries and mutations. All collection sub-fields
 * use @BatchMapping to prevent N+1.
 */
@Controller
public class OrgHierarchyResolver {

    private final OrgHierarchyService orgHierarchyService;

    public OrgHierarchyResolver(OrgHierarchyService orgHierarchyService) {
        this.orgHierarchyService = orgHierarchyService;
    }

    @QueryMapping
    public OrgUnitDto orgUnit(@Argument UUID id) {
        return orgHierarchyService.getUnit(id);
    }

    @QueryMapping
    public List<OrgUnitDto> orgUnitTree(@Argument UUID rootId) {
        return orgHierarchyService.getTree(rootId);
    }

    @MutationMapping
    public OrgUnitDto createOrgUnit(@Argument CreateOrgUnitCommand input) {
        return orgHierarchyService.createUnit(input);
    }

    @MutationMapping
    public OrgUnitDto updateOrgUnit(@Argument UUID id, @Argument UpdateOrgUnitCommand input) {
        return orgHierarchyService.updateUnit(id, input);
    }

    @MutationMapping
    public OrgUnitDto moveOrgUnit(@Argument UUID id, @Argument UUID newParentId) {
        return orgHierarchyService.moveUnit(new MoveOrgUnitCommand(id, newParentId));
    }

    @MutationMapping
    public boolean deactivateOrgUnit(@Argument UUID id, @Argument UUID reassignTo) {
        orgHierarchyService.deactivateUnit(id, reassignTo);
        return true;
    }

    @BatchMapping
    public Map<OrgUnitDto, List<OrgUnitDto>> children(List<OrgUnitDto> parents) {
        var tree = orgHierarchyService.getTree(null);
        return parents.stream().collect(Collectors.toMap(parent -> parent,
                parent -> tree.stream().filter(u -> parent.id().equals(u.parentId())).toList()));
    }

    @BatchMapping
    public Map<OrgUnitDto, List<OrgUnitDto>> ancestors(List<OrgUnitDto> units) {
        return units.stream().collect(Collectors.toMap(unit -> unit,
                unit -> orgHierarchyService.getAncestors(unit.id())));
    }
}
