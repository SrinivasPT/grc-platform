package com.grcplatform.api.repository;

import com.grcplatform.core.domain.UserOrgUnit;
import com.grcplatform.core.repository.UserOrgUnitRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserOrgUnitRepositoryAdapter implements UserOrgUnitRepository {

    private final SpringUserOrgUnitRepository jpa;

    public UserOrgUnitRepositoryAdapter(SpringUserOrgUnitRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public UserOrgUnit save(UserOrgUnit membership) {
        return jpa.save(membership);
    }

    @Override
    public void delete(UUID userId, UUID orgUnitId) {
        jpa.deleteById(new UserOrgUnit.Id(userId, orgUnitId));
    }

    @Override
    public List<UserOrgUnit> findByUserId(UUID userId) {
        return jpa.findByIdUserId(userId);
    }

    @Override
    public List<UserOrgUnit> findByOrgUnitId(UUID orgUnitId) {
        return jpa.findByIdOrgUnitId(orgUnitId);
    }

    @Override
    public Optional<UserOrgUnit> findPrimaryByUserId(UUID userId) {
        return jpa.findPrimaryByUserId(userId);
    }

    @Override
    public long countByOrgUnitId(UUID orgUnitId) {
        return jpa.countByIdOrgUnitId(orgUnitId);
    }
}
