package com.grcplatform.control;

import java.time.LocalDate;
import java.util.UUID;

public record RecordTestResultCommand(UUID controlRecordId, LocalDate testDate, String testResult,
        int exceptionsCount, String notes) {
}
