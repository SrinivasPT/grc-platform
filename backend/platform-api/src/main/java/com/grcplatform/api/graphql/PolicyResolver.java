package com.grcplatform.api.graphql;

import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import com.grcplatform.policy.AcknowledgePolicyCommand;
import com.grcplatform.policy.PolicyAcknowledgmentDto;
import com.grcplatform.policy.PolicyService;

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
        return policyService.acknowledgePolicy(
                new AcknowledgePolicyCommand(policyRecordId, policyVersion, ipAddress));
    }
}
