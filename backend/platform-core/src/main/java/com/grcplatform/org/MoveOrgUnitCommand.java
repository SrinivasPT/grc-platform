package com.grcplatform.org;

import java.util.UUID;

public record MoveOrgUnitCommand(UUID unitId, UUID newParentId) {
}
