package com.grcplatform.api.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.grcplatform.core.domain.WorkflowInstance;

interface SpringWorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {

    @Query("SELECT i FROM WorkflowInstance i WHERE i.orgId = :orgId AND i.recordId = :recordId AND i.status = 'active'")
    Optional<WorkflowInstance> findActiveByRecordId(@Param("orgId") UUID orgId,
            @Param("recordId") UUID recordId);

    @Modifying
    @Query(value = """
            UPDATE workflow_instances
            SET current_state = :newState,
                status       = :newStatus,
                entered_state_at = SYSUTCDATETIME(),
                updated_at   = SYSUTCDATETIME(),
                version      = version + 1
            WHERE id = :id AND version = :expectedVersion
            """, nativeQuery = true)
    int updateStateIfVersion(@Param("id") UUID id, @Param("newState") String newState,
            @Param("newStatus") String newStatus, @Param("expectedVersion") int expectedVersion);
}
