package com.grcplatform.api.graphql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;
import com.grcplatform.api.graphql.dto.FieldValueProjection;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.domain.FieldValueDate;
import com.grcplatform.core.domain.FieldValueNumber;
import com.grcplatform.core.domain.FieldValueReference;
import com.grcplatform.core.domain.FieldValueText;
import com.grcplatform.core.dto.RecordDto;
import com.grcplatform.core.repository.FieldValueDateRepository;
import com.grcplatform.core.repository.FieldValueNumberRepository;
import com.grcplatform.core.repository.FieldValueReferenceRepository;
import com.grcplatform.core.repository.FieldValueTextRepository;

/**
 * Resolves the fieldValues sub-field on GrcRecord using @BatchMapping to prevent N+1 queries. One
 * SQL query is issued per field value type (text, number, date, reference) for the entire batch — 4
 * queries total regardless of how many records are in the list.
 */
@Controller
public class FieldValueBatchResolver {

    private final FieldValueTextRepository textRepo;
    private final FieldValueNumberRepository numberRepo;
    private final FieldValueDateRepository dateRepo;
    private final FieldValueReferenceRepository referenceRepo;

    public FieldValueBatchResolver(FieldValueTextRepository textRepo,
            FieldValueNumberRepository numberRepo, FieldValueDateRepository dateRepo,
            FieldValueReferenceRepository referenceRepo) {
        this.textRepo = textRepo;
        this.numberRepo = numberRepo;
        this.dateRepo = dateRepo;
        this.referenceRepo = referenceRepo;
    }

    @BatchMapping(typeName = "GrcRecord")
    public Map<RecordDto, List<FieldValueProjection>> fieldValues(List<RecordDto> records) {
        var orgId = SessionContextHolder.current().orgId();
        var recordIds = records.stream().map(RecordDto::id).toList();

        var textByRecord = textRepo.findByRecordIds(recordIds, orgId).stream()
                .collect(Collectors.groupingBy(FieldValueText::getRecordId));
        var numberByRecord = numberRepo.findByRecordIds(recordIds, orgId).stream()
                .collect(Collectors.groupingBy(FieldValueNumber::getRecordId));
        var dateByRecord = dateRepo.findByRecordIds(recordIds, orgId).stream()
                .collect(Collectors.groupingBy(FieldValueDate::getRecordId));
        var refByRecord = referenceRepo.findByRecordIds(recordIds, orgId).stream()
                .collect(Collectors.groupingBy(FieldValueReference::getRecordId));

        var result = new LinkedHashMap<RecordDto, List<FieldValueProjection>>(records.size());
        for (var record : records) {
            var projections = new ArrayList<FieldValueProjection>();
            textByRecord.getOrDefault(record.id(), List.of())
                    .forEach(v -> projections.add(FieldValueProjection.fromText(v)));
            numberByRecord.getOrDefault(record.id(), List.of())
                    .forEach(v -> projections.add(FieldValueProjection.fromNumber(v)));
            dateByRecord.getOrDefault(record.id(), List.of())
                    .forEach(v -> projections.add(FieldValueProjection.fromDate(v)));
            refByRecord.getOrDefault(record.id(), List.of())
                    .forEach(v -> projections.add(FieldValueProjection.fromReference(v)));
            result.put(record, projections);
        }
        return result;
    }
}
