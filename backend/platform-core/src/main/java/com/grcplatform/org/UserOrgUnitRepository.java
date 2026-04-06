package com.grcplatform.org;

import com.grcplatform.org.UserOrgUnit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserOrgUnitRepository {

    UserOrgUnit save(UserOrgUnit membership);

    void delete(UUID userId, UUID orgUnitId);

    List<UserOrgUnit> findByUserId(UUID userId);

    List<UserOrgUnit> findByOrgUnitId(UUID orgUnitId);

    Optional<UserOrgUnit> findPrimaryByUserId(UUID userId);

    long countByOrgUnitId(UUID orgUnitId);
}
