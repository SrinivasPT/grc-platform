package com.grcplatform.api.graphql;

import com.grcplatform.api.graphql.dto.CreateRecordInput;
import com.grcplatform.api.graphql.dto.UpdateRecordInput;
import com.grcplatform.core.dto.*;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.service.RecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordResolverTest {

    @Mock
    private RecordService recordService;

    private RecordResolver resolver;

    private static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID APP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID RECORD_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @BeforeEach
    void setUp() {
        resolver = new RecordResolver(recordService);
    }

    @Test
    void getRecord_delegatesToRecordService_andReturnsDto() {
        var dto = sampleRecordDto();
        when(recordService.get(RECORD_ID)).thenReturn(dto);

        var result = resolver.record(RECORD_ID);

        assertThat(result).isEqualTo(dto);
        verify(recordService).get(RECORD_ID);
    }

    @Test
    void getRecord_whenNotFound_propagatesRecordNotFoundException() {
        when(recordService.get(RECORD_ID))
                .thenThrow(new RecordNotFoundException("GrcRecord", RECORD_ID));

        assertThatThrownBy(() -> resolver.record(RECORD_ID))
                .isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void listRecords_returnsPage() {
        var page = new Page<>(List.of(sampleSummaryDto()), 0, 20, 1L);
        when(recordService.list(any(RecordListQuery.class))).thenReturn(page);

        var result = resolver.records(APP_ID, 0, 20);

        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void createRecord_delegatesToRecordService_andReturnsDto() {
        var input = new CreateRecordInput(APP_ID, "Risk: Data Breach", List.of(), "idem-key-1");
        var dto = sampleRecordDto();
        when(recordService.create(any(CreateRecordCommand.class))).thenReturn(dto);

        var result = resolver.createRecord(input);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    void updateRecord_delegatesToRecordService_andReturnsDto() {
        var input = new UpdateRecordInput(RECORD_ID, "Updated Name", List.of());
        var dto = sampleRecordDto();
        when(recordService.update(any(UpdateRecordCommand.class))).thenReturn(dto);

        var result = resolver.updateRecord(input);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    void deleteRecord_callsSoftDelete_andReturnsTrue() {
        doNothing().when(recordService).softDelete(RECORD_ID);

        var result = resolver.deleteRecord(RECORD_ID);

        assertThat(result).isTrue();
        verify(recordService).softDelete(RECORD_ID);
    }

    // ---- helpers ----

    private RecordDto sampleRecordDto() {
        return new RecordDto(RECORD_ID, ORG_ID, APP_ID, "Risk: Data Breach", "RISK-0001", "active",
                null, Map.of(), Instant.now(), Instant.now(), 1L);
    }

    private RecordSummaryDto sampleSummaryDto() {
        return new RecordSummaryDto(RECORD_ID, "Risk: Data Breach", "RISK-0001", "active",
                Instant.now());
    }
}
