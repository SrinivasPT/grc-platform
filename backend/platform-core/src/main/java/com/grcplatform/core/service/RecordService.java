package com.grcplatform.core.service;

import java.util.UUID;
import com.grcplatform.core.dto.CreateRecordCommand;
import com.grcplatform.core.dto.Page;
import com.grcplatform.core.dto.RecordDto;
import com.grcplatform.core.dto.RecordListQuery;
import com.grcplatform.core.dto.RecordSummaryDto;
import com.grcplatform.core.dto.UpdateRecordCommand;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.exception.ValidationException;

/**
 * Primary service for GRC record lifecycle management. All methods resolve orgId from the bound
 * SessionContext — never accept it as a parameter. All mutations must log to audit_log within the
 * same transaction.
 */
public interface RecordService {

    /**
     * Creates a new record and evaluates all COMPUTE rules.
     *
     * @throws ValidationException if VALIDATE rules fail
     */
    RecordDto create(CreateRecordCommand command);

    /**
     * Updates mutable record fields and re-evaluates COMPUTE rules.
     *
     * @throws RecordNotFoundException if the record does not exist in the caller's org
     * @throws ValidationException if VALIDATE rules fail
     */
    RecordDto update(UpdateRecordCommand command);

    /**
     * Soft-deletes a record by setting deleted_at.
     *
     * @throws RecordNotFoundException if the record does not exist in the caller's org
     */
    void softDelete(UUID recordId);

    /**
     * Retrieves a single record by ID, enforcing org isolation.
     *
     * @throws RecordNotFoundException if the record does not exist or belongs to another org
     */
    RecordDto get(UUID recordId);

    /**
     * Returns a paginated list of records for the given application.
     */
    Page<RecordSummaryDto> list(RecordListQuery query);
}
