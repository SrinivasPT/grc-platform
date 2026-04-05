package com.grcplatform.core.workflow;

/**
 * Service interface for workflow state machine operations. Interface lives in platform-core; impl
 * in platform-workflow.
 */
public interface WorkflowService {

    /**
     * Starts a new workflow instance for the given record using the active workflow definition for
     * that record's application.
     */
    WorkflowInstanceDto start(StartWorkflowCommand command);

    /**
     * Executes a state machine transition. Validates actor permissions, guard conditions, and
     * applies the optimistic lock. Publishes outbox events for all on-enter actions.
     *
     * @throws WorkflowTransitionException if the transition is not allowed
     * @throws WorkflowConcurrentModificationException if the optimistic lock check fails
     */
    WorkflowInstanceDto transition(TransitionCommand command);

    /** Returns the active workflow instance for the given record, or null if none. */
    WorkflowInstanceDto findByRecordId(java.util.UUID orgId, java.util.UUID recordId);
}
