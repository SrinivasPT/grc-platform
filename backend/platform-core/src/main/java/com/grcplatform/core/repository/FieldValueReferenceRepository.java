package com.grcplatform.core.repository;

import com.grcplatform.core.domain.FieldValueReference;

import java.util.List;
import java.util.UUID;

public interface FieldValueReferenceRepository {

    void saveAll(List<FieldValueReference> values);

    List<FieldValueReference> findByRecordId(UUID recordId, UUID orgId);

    List<FieldValueReference> findByRecordIds(List<UUID> recordIds, UUID orgId);

    List<FieldValueReference> findByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId);

    void deleteByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId);
}
