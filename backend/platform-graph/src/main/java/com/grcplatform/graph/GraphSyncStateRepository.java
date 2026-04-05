package com.grcplatform.graph;

import java.util.UUID;

/**
 * Persists and reads the last-synced Change Tracking version per org.
 */
public interface GraphSyncStateRepository {

    long getLastSyncVersion(UUID orgId, String entityType);

    void updateLastSyncVersion(UUID orgId, String entityType, long version);
}
