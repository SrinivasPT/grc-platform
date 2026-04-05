package com.grcplatform.workflow;

import com.grcplatform.core.domain.WorkflowTask;
import com.grcplatform.core.repository.WorkflowTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Scheduled job that escalates overdue workflow tasks.
 *
 * Design rules:
 * - Runs hourly on a virtual thread.
 * - Escalation boundary: if no manager is available, task is escalated to a system-admin sentinel UUID.
 * - All escalation notifications are published to the event_outbox (never sent directly).
 * - org_id context is NOT required here — tasks carry their own orgId.
 */
public class EscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscalationScheduler.class);

    /** Sentinel org ID used when scanning across all orgs (background worker context). */
    private static final UUID SYSTEM_ORG = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final WorkflowTaskRepository taskRepository;
    private final WorkflowOutboxPublisher outboxPublisher;
    private final EscalationManagerResolver managerResolver;

    public EscalationScheduler(WorkflowTaskRepository taskRepository,
            WorkflowOutboxPublisher outboxPublisher,
            EscalationManagerResolver managerResolver) {
        this.taskRepository = taskRepository;
        this.outboxPublisher = outboxPublisher;
        this.managerResolver = managerResolver;
    }

    @Scheduled(fixedDelayString = "${grc.workflow.escalation.interval-ms:3600000}")
    public void escalateOverdueTasks() {
        Instant cutoff = Instant.now();
        List<WorkflowTask> overdue = taskRepository.findOverdueTasks(SYSTEM_ORG, cutoff);
        log.debug("Escalation run: {} overdue tasks found", overdue.size());
        overdue.forEach(this::escalate);
    }

    void escalate(WorkflowTask task) {
        UUID manager = managerResolver.resolveManager(task.getOrgId(), task.getAssignedTo());
        if (manager == null) {
            log.warn("No manager found for task {} (orgId={}, assignedTo={}). Skipping escalation.",
                    task.getId(), task.getOrgId(), task.getAssignedTo());
            return;
        }
        task.setEscalatedTo(manager);
        task.setAssignedTo(manager);
        task.setStatus("escalated");
        taskRepository.save(task);
        outboxPublisher.publishEscalated(task.getOrgId(), task.getId(), task.getInstanceId(),
                task.getAssignedTo(), manager);
        log.info("Task {} escalated to manager {} (orgId={})", task.getId(), manager,
                task.getOrgId());
    }
}
