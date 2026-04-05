package com.grcplatform.core.audit;

import com.grcplatform.core.context.SessionContextHolder;

import java.util.UUID;

/**
 * Immutable audit event record capturing what changed and who changed it. orgId is resolved from
 * SessionContext.
 */
public record AuditEvent(UUID orgId, String operation, UUID entityId, UUID actorId, String oldValue,
        String newValue) {
    /**
     * Convenience factory that reads orgId from the active SessionContext.
     */
    public static AuditEvent of(String operation, UUID entityId, UUID actorId, String oldValue,
            String newValue) {
        return new AuditEvent(SessionContextHolder.current().orgId(), operation, entityId, actorId,
                oldValue, newValue);
    }
}
