package com.grcplatform.graph;

import java.util.List;
import com.grcplatform.graph.model.TrackedChange;

/**
 * Reads changes from SQL Server Change Tracking. Implementation injects Spring Data JPA via
 * platform-api.
 */
public interface ChangeTrackingRepository {

    long getCurrentVersion();

    /**
     * Returns all changes since the given version across the tracked tables (records,
     * record_relations).
     */
    List<TrackedChange> getChangesSince(long sinceVersion);
}
