package com.grcplatform.core.repository;

import com.grcplatform.core.domain.WorkflowHistory;

import java.util.List;
import java.util.UUID;

public interface WorkflowHistoryRepository {

    WorkflowHistory save(WorkflowHistory history);

    List<WorkflowHistory> findByInstanceId(UUID instanceId);
}
