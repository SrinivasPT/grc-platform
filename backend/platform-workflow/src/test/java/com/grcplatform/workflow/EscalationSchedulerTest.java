package com.grcplatform.workflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grcplatform.core.domain.WorkflowTask;
import com.grcplatform.core.repository.EventOutboxRepository;
import com.grcplatform.core.repository.WorkflowTaskRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EscalationSchedulerTest {

    @Mock
    WorkflowTaskRepository taskRepo;
    @Mock
    EventOutboxRepository outboxRepo;
    @Mock
    EscalationManagerResolver managerResolver;

    EscalationScheduler scheduler;
    UUID orgId;
    UUID managerId;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        WorkflowOutboxPublisher publisher = new WorkflowOutboxPublisher(outboxRepo, objectMapper);
        scheduler = new EscalationScheduler(taskRepo, publisher, managerResolver);
        orgId = UUID.randomUUID();
        managerId = UUID.randomUUID();
    }

    @Test
    void escalation_reassignsTasks_afterDeadlineExpires() {
        WorkflowTask task = overdueTask();
        when(taskRepo.findOverdueTasks(any(), any())).thenReturn(List.of(task));
        when(managerResolver.resolveManager(orgId, task.getAssignedTo())).thenReturn(managerId);

        scheduler.escalateOverdueTasks();

        verify(taskRepo).save(argThat(t -> managerId.equals(((WorkflowTask) t).getAssignedTo())
                && "escalated".equals(((WorkflowTask) t).getStatus())));
        verify(outboxRepo).save(any());
    }

    @Test
    void escalation_skipsTask_whenNoManagerFound() {
        WorkflowTask task = overdueTask();
        when(taskRepo.findOverdueTasks(any(), any())).thenReturn(List.of(task));
        when(managerResolver.resolveManager(orgId, task.getAssignedTo())).thenReturn(null);

        scheduler.escalateOverdueTasks();

        verify(taskRepo, never()).save(any());
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void escalation_publishesToOutbox_notDirectNotification() {
        WorkflowTask task = overdueTask();
        when(taskRepo.findOverdueTasks(any(), any())).thenReturn(List.of(task));
        when(managerResolver.resolveManager(orgId, task.getAssignedTo())).thenReturn(managerId);

        scheduler.escalateOverdueTasks();

        // Side effect goes through outbox — never a direct notification call
        verify(outboxRepo, atLeastOnce()).save(any());
    }

    private WorkflowTask overdueTask() {
        WorkflowTask task = new WorkflowTask();
        UUID assignedTo = UUID.randomUUID();
        setField(task, "id", UUID.randomUUID());
        setField(task, "orgId", orgId);
        setField(task, "instanceId", UUID.randomUUID());
        setField(task, "taskKey", "review");
        setField(task, "assignedTo", assignedTo);
        setField(task, "dueDate", Instant.now().minus(1, ChronoUnit.DAYS));
        setField(task, "status", "pending");
        setField(task, "createdAt", Instant.now().minus(2, ChronoUnit.DAYS));
        setField(task, "version", 0L);
        return task;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
