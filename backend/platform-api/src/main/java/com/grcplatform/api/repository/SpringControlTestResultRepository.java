package com.grcplatform.api.repository;

import com.grcplatform.control.ControlTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface SpringControlTestResultRepository extends JpaRepository<ControlTestResult, UUID> {

    @Query("""
            SELECT t FROM ControlTestResult t
            WHERE t.orgId = :orgId
              AND t.controlRecordId = :controlRecordId
              AND t.testDate >= :since
            ORDER BY t.testDate DESC
            """)
    List<ControlTestResult> findByOrgIdAndControlRecordIdSince(@Param("orgId") UUID orgId,
            @Param("controlRecordId") UUID controlRecordId, @Param("since") LocalDate since);
}
