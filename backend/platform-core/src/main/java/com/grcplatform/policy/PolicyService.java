package com.grcplatform.policy;

import java.util.List;
import java.util.UUID;

public interface PolicyService {

    /** Records the calling user's acknowledgment for a published policy. */
    PolicyAcknowledgmentDto acknowledgePolicy(AcknowledgePolicyCommand cmd);

    List<PolicyAcknowledgmentDto> getAcknowledgments(UUID policyRecordId);

    /** Returns how many users have acknowledged this policy version. */
    long countAcknowledgments(UUID policyRecordId);

    /** Returns all policies the calling user has acknowledged. */
    List<PolicyAcknowledgmentDto> getMyAcknowledgments();
}
