package com.grcplatform.api.graphql.dto;

import java.util.UUID;

public record StartWorkflowInput(UUID recordId, UUID applicationId, String idempotencyKey) {}
