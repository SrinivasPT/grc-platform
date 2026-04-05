package com.grcplatform.api.repository;

import com.grcplatform.core.domain.FieldValueText;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringFieldValueTextRepository extends JpaRepository<FieldValueText, UUID> {

    @Query("SELECT v FROM FieldValueText v WHERE v.recordId = :rid AND v.orgId = :oid")
    List<FieldValueText> findByRecordIdAndOrgId(@Param("rid") UUID recordId,
            @Param("oid") UUID orgId);

    @Query("SELECT v FROM FieldValueText v WHERE v.recordId IN :rids AND v.orgId = :oid")
    List<FieldValueText> findByRecordIdsAndOrgId(@Param("rids") List<UUID> recordIds,
            @Param("oid") UUID orgId);

    @Query("""
            SELECT v FROM FieldValueText v
            WHERE v.recordId = :rid AND v.fieldDefId = :fid
            """)
    Optional<FieldValueText> findByRecordIdAndFieldDefId(@Param("rid") UUID recordId,
            @Param("fid") UUID fieldDefId);

    @Modifying
    @Query("""
            DELETE FROM FieldValueText v
            WHERE v.recordId = :rid AND v.fieldDefId = :fid
            """)
    void deleteByRecordIdAndFieldDefId(@Param("rid") UUID recordId, @Param("fid") UUID fieldDefId);
}
