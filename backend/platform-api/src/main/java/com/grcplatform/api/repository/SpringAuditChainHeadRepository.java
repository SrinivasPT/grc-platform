package com.grcplatform.api.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.grcplatform.core.domain.AuditChainHead;

interface SpringAuditChainHeadRepository extends JpaRepository<AuditChainHead, UUID> {

    Optional<AuditChainHead> findByOrgId(UUID orgId);
}
