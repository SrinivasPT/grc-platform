package com.grcplatform.api.repository;

import com.grcplatform.core.domain.FieldValueNumber;
import com.grcplatform.core.repository.FieldValueNumberRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FieldValueNumberRepositoryAdapter implements FieldValueNumberRepository {

    private final SpringFieldValueNumberRepository jpa;

    public FieldValueNumberRepositoryAdapter(SpringFieldValueNumberRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void saveAll(List<FieldValueNumber> values) {
        jpa.saveAll(values);
    }

    @Override
    public List<FieldValueNumber> findByRecordId(UUID recordId, UUID orgId) {
        return jpa.findByRecordIdAndOrgId(recordId, orgId);
    }

    @Override
    public List<FieldValueNumber> findByRecordIds(List<UUID> recordIds, UUID orgId) {
        return jpa.findByRecordIdsAndOrgId(recordIds, orgId);
    }

    @Override
    public Optional<FieldValueNumber> findByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId) {
        return jpa.findByRecordIdAndFieldDefId(recordId, fieldDefId);
    }
}
