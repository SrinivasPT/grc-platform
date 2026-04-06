package com.grcplatform.control;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "control_test_results")
@Getter
public class ControlTestResult {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "control_record_id", nullable = false, updatable = false)
    private UUID controlRecordId;

    @Column(name = "tester_id", nullable = false, updatable = false)
    private UUID testerId;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Column(name = "test_result", nullable = false, length = 30)
    private String testResult;

    @Column(name = "exceptions_count", nullable = false)
    private int exceptionsCount;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public static ControlTestResult create(UUID controlRecordId, UUID orgId, UUID testerId,
            LocalDate testDate, String testResult, int exceptionsCount, String notes) {
        var r = new ControlTestResult();
        r.controlRecordId = controlRecordId;
        r.orgId = orgId;
        r.testerId = testerId;
        r.testDate = testDate;
        r.testResult = testResult;
        r.exceptionsCount = exceptionsCount;
        r.notes = notes;
        return r;
    }
}
