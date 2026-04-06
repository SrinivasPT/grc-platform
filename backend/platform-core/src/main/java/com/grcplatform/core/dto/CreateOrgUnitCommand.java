package com.grcplatform.core.dto;

import java.util.UUID;

public record CreateOrgUnitCommand(String name, String unitType, String code, String description,
        UUID parentId, UUID managerId, int displayOrder) {
}
