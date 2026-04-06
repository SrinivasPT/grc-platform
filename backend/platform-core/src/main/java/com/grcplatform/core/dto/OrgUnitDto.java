package com.grcplatform.core.dto;

import java.util.UUID;

public record OrgUnitDto(UUID id, UUID orgId, UUID parentId, String path, int depth,
        String unitType, String code, String name, String description, UUID managerId,
        int displayOrder, boolean active) {
}
