package com.grcplatform.api.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import com.grcplatform.core.domain.FieldValueDate;
import com.grcplatform.core.repository.FieldValueDateRepository;

@Repository
public class FieldValueDateRepositoryAdapter implements FieldValueDateRepository {

    private final SpringFieldValueDateRepository jpa;

    public FieldValueDateRepositoryAdapter(SpringFieldValueDateRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void saveAll(List<FieldValueDate> values) {
        jpa.saveAll(values);
    }

    @Override
    public List<FieldValueDate> findByRecordId(UUID recordId, UUID orgId) {
        return jpa.findByRecordIdAndOrgId(recordId, orgId);
    }

    @Override
    public List<FieldValueDate> findByRecordIds(List<UUID> recordIds, UUID orgId) {
        return jpa.findByRecordIdsAndOrgId(recordIds, orgId);
    }

    @Override
    public Optional<FieldValueDate> findByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId) {
        return jpa.findByRecordIdAndFieldDefId(recordId, fieldDefId);
    }
}
