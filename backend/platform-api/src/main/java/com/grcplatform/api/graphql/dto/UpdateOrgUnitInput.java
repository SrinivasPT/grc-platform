package com.grcplatform.api.graphql.dto;

import java.util.UUID;

public record UpdateOrgUnitInput(String name, String description, UUID managerId) {
}
