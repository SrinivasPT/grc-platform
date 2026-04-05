package com.grcplatform.core.repository;

import com.grcplatform.core.domain.FieldDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldDefinitionRepository {

    FieldDefinition save(FieldDefinition fieldDefinition);

    Optional<FieldDefinition> findByIdAndOrgId(UUID id, UUID orgId);

    List<FieldDefinition> findByApplicationIdAndOrgId(UUID applicationId, UUID orgId);

    List<FieldDefinition> findByIds(List<UUID> ids, UUID orgId);
}
