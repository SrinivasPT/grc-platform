package com.grcplatform.api.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.grcplatform.org.OrgHierarchyService;
import com.grcplatform.org.OrgHierarchyServiceImpl;
import com.grcplatform.org.OrgUnitRepository;
import com.grcplatform.org.UserOrgUnitRepository;
import com.grcplatform.org.command.CreateOrgUnitHandler;
import com.grcplatform.org.command.MoveOrgUnitHandler;
import com.grcplatform.workflow.EscalationManagerResolver;

@Configuration
public class OrgSliceConfig {

    @Bean
    public CreateOrgUnitHandler createOrgUnitHandler(OrgUnitRepository orgUnitRepository) {
        return new CreateOrgUnitHandler(orgUnitRepository, List.of());
    }

    @Bean
    public MoveOrgUnitHandler moveOrgUnitHandler(OrgUnitRepository orgUnitRepository) {
        return new MoveOrgUnitHandler(orgUnitRepository, List.of());
    }

    @Bean
    public OrgHierarchyService orgHierarchyService(OrgUnitRepository orgUnitRepository,
            UserOrgUnitRepository userOrgUnitRepository, CreateOrgUnitHandler createOrgUnitHandler,
            MoveOrgUnitHandler moveOrgUnitHandler) {
        return new OrgHierarchyServiceImpl(orgUnitRepository, userOrgUnitRepository,
                createOrgUnitHandler, moveOrgUnitHandler);
    }

    @Bean
    public EscalationManagerResolver escalationManagerResolver(
            OrgHierarchyService orgHierarchyService) {
        return new OrgHierarchyManagerResolver(orgHierarchyService);
    }
}
