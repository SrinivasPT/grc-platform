package com.grcplatform.api.repository;

import com.grcplatform.core.domain.FieldDefinition;
import com.grcplatform.core.repository.FieldDefinitionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FieldDefinitionRepositoryAdapter implements FieldDefinitionRepository {

    private final SpringFieldDefinitionRepository jpa;

    public FieldDefinitionRepositoryAdapter(SpringFieldDefinitionRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public FieldDefinition save(FieldDefinition fieldDefinition) {
        return jpa.save(fieldDefinition);
    }

    @Override
    public Optional<FieldDefinition> findByIdAndOrgId(UUID id, UUID orgId) {
        return jpa.findByIdAndOrgId(id, orgId);
    }

    @Override
    public List<FieldDefinition> findByApplicationIdAndOrgId(UUID applicationId, UUID orgId) {
        return jpa.findByApplicationIdAndOrgId(applicationId, orgId);
    }

    @Override
    public List<FieldDefinition> findByIds(List<UUID> ids, UUID orgId) {
        return jpa.findByIdsAndOrgId(ids, orgId);
    }
}
