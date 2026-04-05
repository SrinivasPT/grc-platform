package com.grcplatform.api.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.grcplatform.core.domain.FieldValueDate;

interface SpringFieldValueDateRepository extends JpaRepository<FieldValueDate, UUID> {

    @Query("SELECT v FROM FieldValueDate v WHERE v.recordId = :rid AND v.orgId = :oid")
    List<FieldValueDate> findByRecordIdAndOrgId(@Param("rid") UUID recordId,
            @Param("oid") UUID orgId);

    @Query("SELECT v FROM FieldValueDate v WHERE v.recordId IN :rids AND v.orgId = :oid")
    List<FieldValueDate> findByRecordIdsAndOrgId(@Param("rids") List<UUID> recordIds,
            @Param("oid") UUID orgId);

    @Query("""
            SELECT v FROM FieldValueDate v
            WHERE v.recordId = :rid AND v.fieldDefId = :fid
            """)
    Optional<FieldValueDate> findByRecordIdAndFieldDefId(@Param("rid") UUID recordId,
            @Param("fid") UUID fieldDefId);
}
