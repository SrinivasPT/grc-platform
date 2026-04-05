package com.grcplatform.core.repository;

import com.grcplatform.core.domain.AuditChainHead;

import java.util.Optional;
import java.util.UUID;

public interface AuditChainHeadRepository {

    Optional<AuditChainHead> findByOrgId(UUID orgId);

    AuditChainHead save(AuditChainHead head);
}
