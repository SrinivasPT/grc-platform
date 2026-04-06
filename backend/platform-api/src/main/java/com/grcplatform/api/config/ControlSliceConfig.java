package com.grcplatform.api.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.grcplatform.control.ControlEffectivenessRepository;
import com.grcplatform.control.ControlService;
import com.grcplatform.control.ControlServiceImpl;
import com.grcplatform.control.ControlTestResultRepository;
import com.grcplatform.control.command.RecordTestResultHandler;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.repository.RecordRelationRepository;

@Configuration
public class ControlSliceConfig {

    @Bean
    public RecordTestResultHandler recordTestResultHandler(
            ControlTestResultRepository testResultRepository,
            ControlEffectivenessRepository effectivenessRepository, AuditService auditService) {
        return new RecordTestResultHandler(testResultRepository, effectivenessRepository,
                auditService, List.of());
    }

    @Bean
    public ControlService controlService(ControlTestResultRepository testResultRepository,
            ControlEffectivenessRepository effectivenessRepository,
            RecordRelationRepository relationRepository,
            RecordTestResultHandler recordTestResultHandler) {
        return new ControlServiceImpl(testResultRepository, effectivenessRepository,
                relationRepository, recordTestResultHandler);
    }
}
