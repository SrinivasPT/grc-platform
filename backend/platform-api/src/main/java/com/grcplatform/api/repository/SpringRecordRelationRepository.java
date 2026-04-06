package com.grcplatform.api.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.grcplatform.core.domain.RecordRelation;

interface SpringRecordRelationRepository extends JpaRepository<RecordRelation, UUID> {

    List<RecordRelation> findByOrgIdAndTargetIdAndRelationTypeAndActiveTrue(UUID orgId,
            UUID targetId, String relationType);

    List<RecordRelation> findByOrgIdAndSourceIdAndRelationTypeAndActiveTrue(UUID orgId,
            UUID sourceId, String relationType);
}
