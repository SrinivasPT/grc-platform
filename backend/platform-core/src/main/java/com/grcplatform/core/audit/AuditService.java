package com.grcplatform.core.audit;

/**
 * Synchronous audit logging interface. Implementations must write to audit_log within the SAME
 * transaction as the mutation. See ADR-005 and §5 of copilot-instructions.md.
 */
public interface AuditService {

    /**
     * Records an immutable, hash-chained audit entry for the given event. MUST be called within the
     * same @Transactional boundary as the mutation.
     */
    void log(AuditEvent event);
}
