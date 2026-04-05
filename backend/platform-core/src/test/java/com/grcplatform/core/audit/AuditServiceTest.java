package com.grcplatform.core.audit;

import com.grcplatform.core.context.SessionContext;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.domain.AuditChainHead;
import com.grcplatform.core.domain.AuditLogEntry;
import com.grcplatform.core.exception.OptimisticLockConflictException;
import com.grcplatform.core.repository.AuditChainHeadRepository;
import com.grcplatform.core.repository.AuditLogRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditChainHeadRepository chainHeadRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditServiceImpl auditService;

    private UUID orgId;
    private SessionContext sessionContext;

    @BeforeEach
    void setUp() {
        auditService = new AuditServiceImpl(chainHeadRepository, auditLogRepository);
        orgId = UUID.randomUUID();
        sessionContext =
                SessionContext.of(orgId, UUID.randomUUID(), "test.user", List.of("admin"), 1);
    }

    @Test
    void log_writesAuditLogEntry_withNonNullHash() {
        AuditChainHead head = AuditChainHead.initFor(orgId);
        when(chainHeadRepository.findByOrgId(orgId)).thenReturn(Optional.of(head));
        when(chainHeadRepository.save(any())).thenReturn(head);

        UUID entityId = UUID.randomUUID();
        UUID actorId = sessionContext.userId();
        AuditEvent event = new AuditEvent(orgId, "RECORD_CREATED", entityId, actorId, null,
                "{\"name\":\"new\"}");

        ScopedValue.where(SessionContextHolder.SESSION, sessionContext)
                .run(() -> auditService.log(event));

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLogEntry saved = captor.getValue();
        assertThat(saved.getEventHash()).isNotNull().hasSize(64);
        assertThat(saved.getOrgId()).isEqualTo(orgId);
        assertThat(saved.getEntityId()).isEqualTo(entityId);
        assertThat(saved.getAction()).isEqualTo("RECORD_CREATED");
    }

    @Test
    void log_chainsHash_toPreviousEntry() {
        AuditChainHead head = AuditChainHead.initFor(orgId);
        when(chainHeadRepository.findByOrgId(orgId)).thenReturn(Optional.of(head));
        when(chainHeadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID actorId = sessionContext.userId();
        AuditEvent event1 =
                new AuditEvent(orgId, "RECORD_CREATED", UUID.randomUUID(), actorId, null, "{}");
        AuditEvent event2 = new AuditEvent(orgId, "RECORD_UPDATED", UUID.randomUUID(), actorId,
                "{}", "{\"name\":\"updated\"}");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);

        ScopedValue.where(SessionContextHolder.SESSION, sessionContext).run(() -> {
            auditService.log(event1);
            auditService.log(event2);
        });

        verify(auditLogRepository, times(2)).save(captor.capture());
        List<AuditLogEntry> entries = captor.getAllValues();
        assertThat(entries.get(1).getPrevHash()).isEqualTo(entries.get(0).getEventHash());
    }

    @Test
    void log_initializesChainHead_whenNoneExists() {
        when(chainHeadRepository.findByOrgId(orgId)).thenReturn(Optional.empty());
        when(chainHeadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent event = new AuditEvent(orgId, "RECORD_CREATED", UUID.randomUUID(),
                sessionContext.userId(), null, "{}");

        ScopedValue.where(SessionContextHolder.SESSION, sessionContext)
                .run(() -> auditService.log(event));

        verify(chainHeadRepository).save(any(AuditChainHead.class));
        verify(auditLogRepository).save(any(AuditLogEntry.class));
    }

    @Test
    void log_retriesOnOptimisticLockConflict_andSucceedsOnSecondAttempt() {
        AuditChainHead head = AuditChainHead.initFor(orgId);

        // First call throws; second call succeeds
        when(chainHeadRepository.findByOrgId(orgId)).thenReturn(Optional.of(head));
        when(chainHeadRepository.save(any())).thenThrow(new OptimisticLockException("conflict"))
                .thenReturn(head);

        AuditEvent event = new AuditEvent(orgId, "RECORD_UPDATED", UUID.randomUUID(),
                sessionContext.userId(), "{}", "{}");

        ScopedValue.where(SessionContextHolder.SESSION, sessionContext)
                .run(() -> auditService.log(event));

        verify(chainHeadRepository, times(2)).save(any());
        verify(auditLogRepository).save(any());
    }

    @Test
    void log_throwsOptimisticLockConflictException_afterThreeFailures() {
        AuditChainHead head = AuditChainHead.initFor(orgId);
        when(chainHeadRepository.findByOrgId(orgId)).thenReturn(Optional.of(head));
        when(chainHeadRepository.save(any()))
                .thenThrow(new OptimisticLockException("always conflict"));

        AuditEvent event = new AuditEvent(orgId, "RECORD_UPDATED", UUID.randomUUID(),
                sessionContext.userId(), "{}", "{}");

        assertThatThrownBy(
                () -> ScopedValue.where(SessionContextHolder.SESSION, sessionContext).call(() -> {
                    auditService.log(event);
                    return null;
                })).isInstanceOf(OptimisticLockConflictException.class);
    }
}
