package com.grcplatform.api.repository;

import com.grcplatform.core.domain.AuditChainHead;
import com.grcplatform.core.repository.AuditChainHeadRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AuditChainHeadRepositoryAdapter implements AuditChainHeadRepository {

    private final SpringAuditChainHeadRepository jpa;

    public AuditChainHeadRepositoryAdapter(SpringAuditChainHeadRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<AuditChainHead> findByOrgId(UUID orgId) {
        return jpa.findByOrgId(orgId);
    }

    @Override
    public AuditChainHead save(AuditChainHead head) {
        return jpa.save(head);
    }
}
