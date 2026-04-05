package com.grcplatform.core.repository;

import com.grcplatform.core.domain.Application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository {

    Application save(Application application);

    Optional<Application> findByIdAndOrgId(UUID id, UUID orgId);

    Optional<Application> findByInternalKeyAndOrgId(String internalKey, UUID orgId);

    List<Application> findByOrgId(UUID orgId);
}
