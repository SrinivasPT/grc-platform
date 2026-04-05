package com.grcplatform.api.config;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.grcplatform.core.repository.IdempotencyKeyRepository;

/**
 * Nightly cleanup of expired idempotency keys to keep the table compact.
 */
@Component
public class IdempotencyKeyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupJob.class);

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public IdempotencyKeyCleanupJob(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void purgeExpiredKeys() {
        var before = Instant.now();
        idempotencyKeyRepository.deleteExpiredBefore(before);
        log.info("Purged expired idempotency keys before {}", before);
    }
}
