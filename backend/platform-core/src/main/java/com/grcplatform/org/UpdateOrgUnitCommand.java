package com.grcplatform.org;

import java.util.UUID;

public record UpdateOrgUnitCommand(String name, String description, UUID managerId) {
}
