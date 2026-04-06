package com.grcplatform.api.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import com.grcplatform.core.domain.RecordRelation;
import com.grcplatform.core.repository.RecordRelationRepository;

@Repository
public class RecordRelationRepositoryAdapter implements RecordRelationRepository {

    private final SpringRecordRelationRepository spring;

    public RecordRelationRepositoryAdapter(SpringRecordRelationRepository spring) {
        this.spring = spring;
    }

    @Override
    public RecordRelation save(RecordRelation relation) {
        return spring.save(relation);
    }

    @Override
    public List<RecordRelation> findByOrgIdAndTargetIdAndRelationType(UUID orgId, UUID targetId,
            String relationType) {
        return spring.findByOrgIdAndTargetIdAndRelationTypeAndActiveTrue(orgId, targetId,
                relationType);
    }

    @Override
    public List<RecordRelation> findByOrgIdAndSourceIdAndRelationType(UUID orgId, UUID sourceId,
            String relationType) {
        return spring.findByOrgIdAndSourceIdAndRelationTypeAndActiveTrue(orgId, sourceId,
                relationType);
    }
}
