package com.grcplatform.api.repository;

import com.grcplatform.core.domain.WorkflowHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringWorkflowHistoryRepository extends JpaRepository<WorkflowHistory, UUID> {

    List<WorkflowHistory> findByInstanceIdOrderByTransitionedAtAsc(UUID instanceId);
}
