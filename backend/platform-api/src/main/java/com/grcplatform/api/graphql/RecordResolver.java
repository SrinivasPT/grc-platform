package com.grcplatform.api.graphql;

import com.grcplatform.api.graphql.dto.CreateRecordInput;
import com.grcplatform.api.graphql.dto.UpdateRecordInput;
import com.grcplatform.core.dto.*;
import com.grcplatform.core.service.RecordService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * GraphQL resolver for GrcRecord queries and mutations. All @TransactionMapping is handled in the
 * service layer — never here. All collection sub-fields (fieldValues) are handled by
 * FieldValueBatchResolver.
 */
@Controller
public class RecordResolver {

    private final RecordService recordService;

    public RecordResolver(RecordService recordService) {
        this.recordService = recordService;
    }

    @QueryMapping
    public RecordDto record(@Argument UUID id) {
        return recordService.get(id);
    }

    @QueryMapping
    public Page<RecordSummaryDto> records(@Argument UUID applicationId, @Argument int page,
            @Argument int size) {
        return recordService.list(RecordListQuery.of(applicationId, page, size));
    }

    @MutationMapping
    public RecordDto createRecord(@Argument CreateRecordInput input) {
        var command = new CreateRecordCommand(input.applicationId(), input.displayName(),
                input.fieldValues(), input.idempotencyKey());
        return recordService.create(command);
    }

    @MutationMapping
    public RecordDto updateRecord(@Argument UpdateRecordInput input) {
        var command =
                new UpdateRecordCommand(input.recordId(), input.displayName(), input.fieldValues());
        return recordService.update(command);
    }

    @MutationMapping
    public boolean deleteRecord(@Argument UUID id) {
        recordService.softDelete(id);
        return true;
    }
}
