package com.grcplatform.api.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import com.grcplatform.core.domain.FieldValueReference;
import com.grcplatform.core.repository.FieldValueReferenceRepository;

@Repository
public class FieldValueReferenceRepositoryAdapter implements FieldValueReferenceRepository {

    private final SpringFieldValueReferenceRepository jpa;

    public FieldValueReferenceRepositoryAdapter(SpringFieldValueReferenceRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void saveAll(List<FieldValueReference> values) {
        jpa.saveAll(values);
    }

    @Override
    public List<FieldValueReference> findByRecordId(UUID recordId, UUID orgId) {
        return jpa.findByRecordIdAndOrgId(recordId, orgId);
    }

    @Override
    public List<FieldValueReference> findByRecordIds(List<UUID> recordIds, UUID orgId) {
        return jpa.findByRecordIdsAndOrgId(recordIds, orgId);
    }

    @Override
    public List<FieldValueReference> findByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId) {
        return jpa.findByRecordIdAndFieldDefId(recordId, fieldDefId);
    }

    @Override
    public void deleteByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId) {
        jpa.deleteByRecordIdAndFieldDefId(recordId, fieldDefId);
    }
}
