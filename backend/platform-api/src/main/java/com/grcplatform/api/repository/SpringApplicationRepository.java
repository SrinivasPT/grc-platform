package com.grcplatform.api.repository;

import com.grcplatform.core.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByIdAndOrgId(UUID id, UUID orgId);

    Optional<Application> findByInternalKeyAndOrgId(String internalKey, UUID orgId);

    List<Application> findByOrgId(UUID orgId);
}
