package com.grcplatform.graph.model;

/**
 * Represents a change event from SQL Server Change Tracking.
 */
public record TrackedChange(String tableName, String operation, // "INSERT" | "UPDATE" | "DELETE"
        String primaryKeyId, String extraJson // JSON of changed column values (for projecting
                                              // properties)
) {
}
