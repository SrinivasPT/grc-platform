package com.grcplatform.core.dto;

import java.util.UUID;

/**
 * Query parameters for paginated record listing.
 */
public record RecordListQuery(UUID applicationId, String status, String searchTerm, int page,
        int pageSize) {
    public RecordListQuery {
        if (page < 0)
            throw new IllegalArgumentException("page must be >= 0");
        if (pageSize < 1 || pageSize > 200)
            throw new IllegalArgumentException("pageSize must be between 1 and 200");
    }

    public static RecordListQuery of(UUID applicationId, int page, int pageSize) {
        return new RecordListQuery(applicationId, null, null, page, pageSize);
    }
}
