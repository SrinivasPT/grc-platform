package com.grcplatform.api.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.grcplatform.core.domain.GraphSyncState;

interface SpringGraphSyncStateRepository extends JpaRepository<GraphSyncState, UUID> {

    Optional<GraphSyncState> findByOrgIdAndEntityType(UUID orgId, String entityType);

    @Modifying
    @Query(value = """
            MERGE INTO graph_sync_state WITH (HOLDLOCK) AS target
            USING (SELECT :orgId AS org_id, :entityType AS entity_type) AS source
            ON (target.org_id = source.org_id AND target.entity_type = source.entity_type)
            WHEN MATCHED THEN
                UPDATE SET last_ct_version = :version, updated_at = SYSUTCDATETIME()
            WHEN NOT MATCHED THEN
                INSERT (id, org_id, entity_type, last_ct_version, updated_at)
                VALUES (NEWID(), :orgId, :entityType, :version, SYSUTCDATETIME());
            """, nativeQuery = true)
    void upsertSyncVersion(@Param("orgId") UUID orgId, @Param("entityType") String entityType,
            @Param("version") long version);
}
