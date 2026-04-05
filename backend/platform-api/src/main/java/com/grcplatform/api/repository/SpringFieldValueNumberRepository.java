package com.grcplatform.api.repository;

import com.grcplatform.core.domain.FieldValueNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringFieldValueNumberRepository extends JpaRepository<FieldValueNumber, UUID> {

    @Query("SELECT v FROM FieldValueNumber v WHERE v.recordId = :rid AND v.orgId = :oid")
    List<FieldValueNumber> findByRecordIdAndOrgId(@Param("rid") UUID recordId,
            @Param("oid") UUID orgId);

    @Query("SELECT v FROM FieldValueNumber v WHERE v.recordId IN :rids AND v.orgId = :oid")
    List<FieldValueNumber> findByRecordIdsAndOrgId(@Param("rids") List<UUID> recordIds,
            @Param("oid") UUID orgId);

    @Query("""
            SELECT v FROM FieldValueNumber v
            WHERE v.recordId = :rid AND v.fieldDefId = :fid
            """)
    Optional<FieldValueNumber> findByRecordIdAndFieldDefId(@Param("rid") UUID recordId,
            @Param("fid") UUID fieldDefId);
}
