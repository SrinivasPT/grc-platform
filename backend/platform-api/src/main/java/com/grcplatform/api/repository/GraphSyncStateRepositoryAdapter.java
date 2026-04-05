package com.grcplatform.api.repository;

import com.grcplatform.core.domain.GraphSyncState;
import com.grcplatform.graph.GraphSyncStateRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class GraphSyncStateRepositoryAdapter implements GraphSyncStateRepository {

    private final SpringGraphSyncStateRepository jpa;

    public GraphSyncStateRepositoryAdapter(SpringGraphSyncStateRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public long getLastSyncVersion(UUID orgId, String entityType) {
        return jpa.findByOrgIdAndEntityType(orgId, entityType)
                .map(GraphSyncState::getLastCtVersion)
                .orElse(0L);
    }

    @Override
    public void updateLastSyncVersion(UUID orgId, String entityType, long version) {
        jpa.upsertSyncVersion(orgId, entityType, version);
    }
}
