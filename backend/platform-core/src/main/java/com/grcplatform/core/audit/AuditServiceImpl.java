package com.grcplatform.core.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.grcplatform.core.domain.AuditChainHead;
import com.grcplatform.core.domain.AuditLogEntry;
import com.grcplatform.core.exception.OptimisticLockConflictException;
import com.grcplatform.core.repository.AuditChainHeadRepository;
import com.grcplatform.core.repository.AuditLogRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;

/**
 * Writes hash-chained, tamper-evident audit entries.
 *
 * Hash formula: SHA-256(prevHash + orgId + entityId + operation + newValue + sequenceNumber)
 * Per-org optimistic lock on audit_chain_head.version — retried up to 3 times. Must be called
 * within the same @Transactional boundary as the mutation (see §5 copilot-instructions).
 */
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);
    private static final int MAX_RETRIES = 3;

    private final AuditChainHeadRepository chainHeadRepository;
    private final AuditLogRepository auditLogRepository;

    public AuditServiceImpl(AuditChainHeadRepository chainHeadRepository,
            AuditLogRepository auditLogRepository) {
        this.chainHeadRepository = chainHeadRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional
    public void log(AuditEvent event) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                attemptLog(event);
                return;
            } catch (OptimisticLockException e) {
                if (attempt == MAX_RETRIES) throw new OptimisticLockConflictException(
                        "audit chain head for org " + event.orgId(), e);
                log.warn("Audit chain head optimistic lock conflict for org {}, attempt {}/{}",
                        event.orgId(), attempt, MAX_RETRIES);
                sleepExponential(attempt);
            }
        }
    }

    private void attemptLog(AuditEvent event) {
        var head = chainHeadRepository.findByOrgId(event.orgId())
                .orElseGet(() -> AuditChainHead.initFor(event.orgId()));
        long seqNum = head.advanceSequence();
        var prevHash = head.getLastHash() != null ? head.getLastHash() : "0".repeat(64);
        var hash = computeHash(prevHash, event, seqNum);
        head.updateHash(hash);
        chainHeadRepository.save(head);

        auditLogRepository.save(AuditLogEntry.create(event.orgId(), seqNum, hash, prevHash,
                event.actorId(), deriveEntityType(event.operation()), event.entityId(),
                event.operation(), event.oldValue(), event.newValue(), null));
    }

    private String computeHash(String prevHash, AuditEvent event, long sequenceNumber) {
        var raw = prevHash + event.orgId() + event.entityId() + event.operation()
                + (event.newValue() != null ? event.newValue() : "") + sequenceNumber;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String deriveEntityType(String operation) {
        int underscoreIdx = operation.indexOf('_');
        return underscoreIdx > 0 ? operation.substring(0, underscoreIdx) : operation;
    }

    private void sleepExponential(int attempt) {
        try {
            Thread.sleep(50L * (1L << attempt));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
