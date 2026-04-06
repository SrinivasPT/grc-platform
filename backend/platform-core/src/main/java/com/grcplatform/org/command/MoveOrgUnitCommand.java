package com.grcplatform.org.command;

import java.util.UUID;

public record MoveOrgUnitCommand(UUID unitId, UUID newParentId) {
}

