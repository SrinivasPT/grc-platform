package com.grcplatform.api.repository;

import com.grcplatform.core.domain.Application;
import com.grcplatform.core.repository.ApplicationRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ApplicationRepositoryAdapter implements ApplicationRepository {

    private final SpringApplicationRepository jpa;

    public ApplicationRepositoryAdapter(SpringApplicationRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Application save(Application application) {
        return jpa.save(application);
    }

    @Override
    public Optional<Application> findByIdAndOrgId(UUID id, UUID orgId) {
        return jpa.findByIdAndOrgId(id, orgId);
    }

    @Override
    public Optional<Application> findByInternalKeyAndOrgId(String internalKey, UUID orgId) {
        return jpa.findByInternalKeyAndOrgId(internalKey, orgId);
    }

    @Override
    public List<Application> findByOrgId(UUID orgId) {
        return jpa.findByOrgId(orgId);
    }
}
