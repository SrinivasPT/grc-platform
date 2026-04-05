package com.grcplatform.core.repository;

import com.grcplatform.core.domain.FieldValueText;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldValueTextRepository {

    void saveAll(List<FieldValueText> values);

    List<FieldValueText> findByRecordId(UUID recordId, UUID orgId);

    List<FieldValueText> findByRecordIds(List<UUID> recordIds, UUID orgId);

    Optional<FieldValueText> findByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId);

    void deleteByRecordIdAndFieldDefId(UUID recordId, UUID fieldDefId);
}
