package com.grcplatform.core.repository;

import com.grcplatform.core.domain.FieldValueNumber;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldValueNumberRepository {

    void saveAll(List<FieldValueNumber> values);

    List<FieldValueNumber> findByRecordId(UUID recordId, UUID orgId);

    List<FieldValueNumber> findByRecordIds(List<UUID> recordIds, UUID orgId);

    Optional<FieldValueNumber> findByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId);
}
