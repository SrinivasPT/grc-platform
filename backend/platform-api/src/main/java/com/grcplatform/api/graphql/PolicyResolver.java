package com.grcplatform.api.graphql;

import com.grcplatform.core.dto.PolicyAcknowledgmentDto;
import com.grcplatform.core.service.PolicyService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class PolicyResolver {

    private final PolicyService policyService;

    public PolicyResolver(PolicyService policyService) {
        this.policyService = policyService;
    }

    @QueryMapping
    public List<PolicyAcknowledgmentDto> policyAcknowledgments(@Argument UUID policyRecordId) {
        return policyService.getAcknowledgments(policyRecordId);
    }

    @QueryMapping
    public List<PolicyAcknowledgmentDto> myPolicyAcknowledgments() {
        return policyService.getMyAcknowledgments();
    }

    @MutationMapping
    public PolicyAcknowledgmentDto acknowledgePolicy(@Argument UUID policyRecordId,
            @Argument String policyVersion, @Argument String ipAddress) {
        return policyService.acknowledgePolicy(policyRecordId, policyVersion, ipAddress);
    }
}
