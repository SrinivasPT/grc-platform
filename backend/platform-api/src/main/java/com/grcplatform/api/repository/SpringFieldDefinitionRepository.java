package com.grcplatform.api.repository;

import com.grcplatform.core.domain.FieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringFieldDefinitionRepository extends JpaRepository<FieldDefinition, UUID> {

    Optional<FieldDefinition> findByIdAndOrgId(UUID id, UUID orgId);

    List<FieldDefinition> findByApplicationIdAndOrgId(UUID applicationId, UUID orgId);

    @Query("SELECT f FROM FieldDefinition f WHERE f.id IN :ids AND f.orgId = :orgId")
    List<FieldDefinition> findByIdsAndOrgId(@Param("ids") List<UUID> ids,
            @Param("orgId") UUID orgId);
}
