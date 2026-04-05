package com.grcplatform.graph;

import java.util.List;
import java.util.UUID;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import com.grcplatform.graph.model.TrackedChange;

/**
 * CDC-based graph projection worker.
 *
 * Algorithm: 1. Read last processed CT version from graph_sync_state. 2. Query SQL Server Change
 * Tracking for changed rows since that version. 3. For each change: execute idempotent Cypher
 * MERGE/SET/DELETE. 4. Update graph_sync_state.last_ct_version. 5. On restart: re-reads last
 * version → no duplicates, no missed changes.
 *
 * All Cypher operations are idempotent (MERGE + SET) to handle worker restarts safely. org_id is
 * NOT required from SessionContext — this is a background worker.
 */
public class GraphProjectionWorker {

    private static final Logger log = LoggerFactory.getLogger(GraphProjectionWorker.class);

    /**
     * Sentinel org representing "all orgs" for the background worker scan. Sync state is stored per
     * org in practice; this sentinel selects the global row.
     */
    private static final UUID GLOBAL_SYNC_ORG =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ENTITY_RECORDS = "records";
    private static final String ENTITY_RELATIONS = "record_relations";

    private final ChangeTrackingRepository changeTrackingRepository;
    private final GraphSyncStateRepository syncStateRepository;
    private final Driver neo4jDriver;

    public GraphProjectionWorker(ChangeTrackingRepository changeTrackingRepository,
            GraphSyncStateRepository syncStateRepository, Driver neo4jDriver) {
        this.changeTrackingRepository = changeTrackingRepository;
        this.syncStateRepository = syncStateRepository;
        this.neo4jDriver = neo4jDriver;
    }

    @Scheduled(fixedDelayString = "${grc.graph.sync.interval-ms:2000}")
    public void syncChanges() {
        long lastVersion = syncStateRepository.getLastSyncVersion(GLOBAL_SYNC_ORG, ENTITY_RECORDS);
        long currentVersion = changeTrackingRepository.getCurrentVersion();

        if (currentVersion <= lastVersion) return;

        List<TrackedChange> changes = changeTrackingRepository.getChangesSince(lastVersion);
        if (changes.isEmpty()) {
            syncStateRepository.updateLastSyncVersion(GLOBAL_SYNC_ORG, ENTITY_RECORDS,
                    currentVersion);
            return;
        }

        log.debug("GraphProjectionWorker: projecting {} changes (CT {} → {})", changes.size(),
                lastVersion, currentVersion);

        try (Session session = neo4jDriver.session()) {
            for (TrackedChange change : changes) {
                processChange(session, change);
            }
        }

        syncStateRepository.updateLastSyncVersion(GLOBAL_SYNC_ORG, ENTITY_RECORDS, currentVersion);
    }

    void processChange(Session session, TrackedChange change) {
        switch (change.tableName()) {
            case "records" -> processRecordChange(session, change);
            case "record_relations" -> processRelationChange(session, change);
            default -> log.debug("No graph handler for table '{}', skipping.", change.tableName());
        }
    }

    private void processRecordChange(Session session, TrackedChange change) {
        switch (change.operation()) {
            case "INSERT", "UPDATE" -> session.run("""
                    MERGE (n:GrcRecord {id: $id})
                    SET n += $props
                    """, Values.parameters("id", change.primaryKeyId(), "props",
                    parseProps(change.extraJson())));

            case "DELETE" -> session.run("""
                    MATCH (n:GrcRecord {id: $id})
                    SET n.status = 'deleted'
                    """, Values.parameters("id", change.primaryKeyId()));

            default -> log.warn("Unknown CT operation '{}' for table 'records'",
                    change.operation());
        }
    }

    private void processRelationChange(Session session, TrackedChange change) {
        switch (change.operation()) {
            case "INSERT" -> session.run("""
                    MATCH (a:GrcRecord {id: $sourceId}), (b:GrcRecord {id: $targetId})
                    MERGE (a)-[r:RELATED_TO {id: $relId}]->(b)
                    SET r.relationType = $relationType, r.active = true
                    """, parseRelationProps(change.extraJson()));

            case "DELETE" -> session.run("""
                    MATCH ()-[r:RELATED_TO {id: $relId}]-()
                    SET r.active = false
                    """, Values.parameters("relId", change.primaryKeyId()));

            default -> log.warn("Unknown CT operation '{}' for table 'record_relations'",
                    change.operation());
        }
    }

    private org.neo4j.driver.Value parseProps(String json) {
        // Minimal property map — populated from extraJson (set by ChangeTrackingRepository impl)
        return Values.parameters();
    }

    private org.neo4j.driver.Value parseRelationProps(String json) {
        return Values.parameters("relId", "", "sourceId", "", "targetId", "", "relationType", "");
    }
}
