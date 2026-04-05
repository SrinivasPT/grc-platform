package com.grcplatform.core.dto;

import java.util.List;

/**
 * Generic paginated result container.
 */
public record Page<T>(List<T> content, int page, int pageSize, long totalElements) {
    public Page {
        content = List.copyOf(content);
    }

    public boolean hasNextPage() {
        return (long) (page + 1) * pageSize < totalElements;
    }
}
