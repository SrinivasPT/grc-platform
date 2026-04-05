package com.grcplatform.graph;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import com.grcplatform.graph.model.TrackedChange;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphProjectionWorkerTest {

    @Mock
    ChangeTrackingRepository changeTrackingRepo;
    @Mock
    GraphSyncStateRepository syncStateRepo;
    @Mock
    Driver neo4jDriver;
    @Mock
    Session neo4jSession;

    GraphProjectionWorker worker;
    UUID orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        worker = new GraphProjectionWorker(changeTrackingRepo, syncStateRepo, neo4jDriver);
        when(neo4jDriver.session()).thenReturn(neo4jSession);
    }

    @Test
    void worker_syncsNewRelation_toNeo4j() {
        when(syncStateRepo.getLastSyncVersion(any(), anyString())).thenReturn(10L);
        when(changeTrackingRepo.getCurrentVersion()).thenReturn(11L);
        TrackedChange change =
                new TrackedChange("records", "INSERT", UUID.randomUUID().toString(), "{}");
        when(changeTrackingRepo.getChangesSince(10L)).thenReturn(List.of(change));

        worker.syncChanges();

        verify(neo4jSession).run(contains("MERGE (n:GrcRecord"), any(org.neo4j.driver.Value.class));
        verify(syncStateRepo).updateLastSyncVersion(any(), anyString(), eq(11L));
    }

    @Test
    void worker_isIdempotent_onReplay() {
        // Running syncChanges twice with the same current version should only project once
        when(syncStateRepo.getLastSyncVersion(any(), anyString())).thenReturn(5L);
        when(changeTrackingRepo.getCurrentVersion()).thenReturn(6L);
        TrackedChange change =
                new TrackedChange("records", "UPDATE", UUID.randomUUID().toString(), "{}");
        when(changeTrackingRepo.getChangesSince(5L)).thenReturn(List.of(change));

        worker.syncChanges();

        // Second call: version has been updated so currentVersion == lastVersion
        when(syncStateRepo.getLastSyncVersion(any(), anyString())).thenReturn(6L);
        worker.syncChanges();

        verify(neo4jSession, times(1)).run(anyString(), any(org.neo4j.driver.Value.class));
    }

    @Test
    void worker_resumesFromLastVersion_onRestart() {
        // Worker reads lastSyncVersion=7 on startup (persisted from previous run)
        when(syncStateRepo.getLastSyncVersion(any(), anyString())).thenReturn(7L);
        when(changeTrackingRepo.getCurrentVersion()).thenReturn(9L);
        when(changeTrackingRepo.getChangesSince(7L)).thenReturn(List
                .of(new TrackedChange("records", "INSERT", UUID.randomUUID().toString(), "{}")));

        worker.syncChanges();

        verify(changeTrackingRepo).getChangesSince(7L);
        verify(syncStateRepo).updateLastSyncVersion(any(), anyString(), eq(9L));
    }

    @Test
    void worker_marksRelationInactive_onSoftDelete() {
        when(syncStateRepo.getLastSyncVersion(any(), anyString())).thenReturn(0L);
        when(changeTrackingRepo.getCurrentVersion()).thenReturn(1L);
        TrackedChange change =
                new TrackedChange("records", "DELETE", UUID.randomUUID().toString(), "{}");
        when(changeTrackingRepo.getChangesSince(0L)).thenReturn(List.of(change));

        worker.syncChanges();

        verify(neo4jSession).run(contains("SET n.status = 'deleted'"),
                any(org.neo4j.driver.Value.class));
    }

    @Test
    void worker_doesNothing_whenVersionUnchanged() {
        when(syncStateRepo.getLastSyncVersion(any(), anyString())).thenReturn(10L);
        when(changeTrackingRepo.getCurrentVersion()).thenReturn(10L);

        worker.syncChanges();

        verify(neo4jDriver, never()).session();
        verify(changeTrackingRepo, never()).getChangesSince(anyLong());
    }
}
