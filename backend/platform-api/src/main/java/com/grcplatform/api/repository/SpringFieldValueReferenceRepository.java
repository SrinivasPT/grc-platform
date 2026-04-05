package com.grcplatform.api.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.grcplatform.core.domain.FieldValueReference;

interface SpringFieldValueReferenceRepository extends JpaRepository<FieldValueReference, UUID> {

    @Query("SELECT v FROM FieldValueReference v WHERE v.recordId = :rid AND v.orgId = :oid")
    List<FieldValueReference> findByRecordIdAndOrgId(@Param("rid") UUID recordId,
            @Param("oid") UUID orgId);

    @Query("SELECT v FROM FieldValueReference v WHERE v.recordId IN :rids AND v.orgId = :oid")
    List<FieldValueReference> findByRecordIdsAndOrgId(@Param("rids") List<UUID> recordIds,
            @Param("oid") UUID orgId);

    @Query("""
            SELECT v FROM FieldValueReference v
            WHERE v.recordId = :rid AND v.fieldDefId = :fid
            """)
    List<FieldValueReference> findByRecordIdAndFieldDefId(@Param("rid") UUID recordId,
            @Param("fid") UUID fieldDefId);

    @Modifying
    @Query("""
            DELETE FROM FieldValueReference v
            WHERE v.recordId = :rid AND v.fieldDefId = :fid
            """)
    void deleteByRecordIdAndFieldDefId(@Param("rid") UUID recordId, @Param("fid") UUID fieldDefId);
}
