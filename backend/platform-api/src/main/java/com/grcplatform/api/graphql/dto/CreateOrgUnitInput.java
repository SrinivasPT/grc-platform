package com.grcplatform.api.graphql.dto;

import java.util.UUID;

public record CreateOrgUnitInput(String name, String unitType, String code, String description,
        UUID parentId, UUID managerId, int displayOrder) {
}
