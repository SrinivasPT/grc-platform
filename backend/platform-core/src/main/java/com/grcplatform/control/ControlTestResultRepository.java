package com.grcplatform.control;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ControlTestResultRepository {
    ControlTestResult save(ControlTestResult result);

    List<ControlTestResult> findByOrgIdAndControlRecordIdSince(UUID orgId, UUID controlRecordId,
            LocalDate since);
}
