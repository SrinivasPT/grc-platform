package com.grcplatform.api.repository;

import com.grcplatform.org.UserOrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringUserOrgUnitRepository extends JpaRepository<UserOrgUnit, UserOrgUnit.Id> {

    List<UserOrgUnit> findByIdUserId(UUID userId);

    List<UserOrgUnit> findByIdOrgUnitId(UUID orgUnitId);

    @Query("SELECT u FROM UserOrgUnit u WHERE u.id.userId = :userId AND u.primary = true")
    Optional<UserOrgUnit> findPrimaryByUserId(@Param("userId") UUID userId);

    long countByIdOrgUnitId(UUID orgUnitId);
}
