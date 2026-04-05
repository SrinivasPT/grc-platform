package com.grcplatform.api.repository;

import com.grcplatform.core.domain.FieldValueText;
import com.grcplatform.core.repository.FieldValueTextRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FieldValueTextRepositoryAdapter implements FieldValueTextRepository {

    private final SpringFieldValueTextRepository jpa;

    public FieldValueTextRepositoryAdapter(SpringFieldValueTextRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void saveAll(List<FieldValueText> values) {
        jpa.saveAll(values);
    }

    @Override
    public List<FieldValueText> findByRecordId(UUID recordId, UUID orgId) {
        return jpa.findByRecordIdAndOrgId(recordId, orgId);
    }

    @Override
    public List<FieldValueText> findByRecordIds(List<UUID> recordIds, UUID orgId) {
        return jpa.findByRecordIdsAndOrgId(recordIds, orgId);
    }

    @Override
    public Optional<FieldValueText> findByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId) {
        return jpa.findByRecordIdAndFieldDefId(recordId, fieldDefId);
    }

    @Override
    public void deleteByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId) {
        jpa.deleteByRecordIdAndFieldDefId(recordId, fieldDefId);
    }
}
