package com.grcplatform.core.repository;

import com.grcplatform.core.domain.RecordRelation;
import java.util.List;
import java.util.UUID;

public interface RecordRelationRepository {
    RecordRelation save(RecordRelation relation);

    List<RecordRelation> findByOrgIdAndTargetIdAndRelationType(UUID orgId, UUID targetId,
            String relationType);

    List<RecordRelation> findByOrgIdAndSourceIdAndRelationType(UUID orgId, UUID sourceId,
            String relationType);
}
