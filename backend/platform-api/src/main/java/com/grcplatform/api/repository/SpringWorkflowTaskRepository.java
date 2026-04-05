package com.grcplatform.api.repository;

import com.grcplatform.core.domain.WorkflowTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringWorkflowTaskRepository extends JpaRepository<WorkflowTask, UUID> {

    List<WorkflowTask> findByInstanceId(UUID instanceId);

    @Query("""
            SELECT t FROM WorkflowTask t
            WHERE t.orgId = :orgId AND t.assignedTo = :assignedTo AND t.status = :status
            """)
    List<WorkflowTask> findByOrgIdAndAssignedToAndStatus(@Param("orgId") UUID orgId,
            @Param("assignedTo") UUID assignedTo, @Param("status") String status);

    @Query("""
            SELECT t FROM WorkflowTask t
            WHERE t.dueDate < :cutoff AND t.status = 'pending'
            """)
    List<WorkflowTask> findOverdueWithCutoff(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE WorkflowTask t SET t.status = 'cancelled' WHERE t.instanceId = :instanceId AND t.status = 'pending'")
    void cancelByInstanceId(@Param("instanceId") UUID instanceId);
}
