package com.grcplatform.api.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import com.grcplatform.core.domain.ControlTestResult;
import com.grcplatform.core.repository.ControlTestResultRepository;

@Repository
public class ControlTestResultRepositoryAdapter implements ControlTestResultRepository {

    private final SpringControlTestResultRepository spring;

    public ControlTestResultRepositoryAdapter(SpringControlTestResultRepository spring) {
        this.spring = spring;
    }

    @Override
    public ControlTestResult save(ControlTestResult result) {
        return spring.save(result);
    }

    @Override
    public List<ControlTestResult> findByOrgIdAndControlRecordIdSince(UUID orgId,
            UUID controlRecordId, LocalDate since) {
        return spring.findByOrgIdAndControlRecordIdSince(orgId, controlRecordId, since);
    }
}
