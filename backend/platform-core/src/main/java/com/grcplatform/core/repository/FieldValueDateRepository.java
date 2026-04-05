package com.grcplatform.core.repository;

import com.grcplatform.core.domain.FieldValueDate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldValueDateRepository {

    void saveAll(List<FieldValueDate> values);

    List<FieldValueDate> findByRecordId(UUID recordId, UUID orgId);

    List<FieldValueDate> findByRecordIds(List<UUID> recordIds, UUID orgId);

    Optional<FieldValueDate> findByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId);
}
