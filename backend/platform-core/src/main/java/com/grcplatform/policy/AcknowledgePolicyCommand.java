package com.grcplatform.policy;

import java.util.UUID;

public record AcknowledgePolicyCommand(UUID policyRecordId, String policyVersion,
        String ipAddress) {
}
