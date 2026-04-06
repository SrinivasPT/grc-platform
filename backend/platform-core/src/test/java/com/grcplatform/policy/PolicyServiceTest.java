package com.grcplatform.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContext;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.exception.ValidationException;
import com.grcplatform.policy.command.AcknowledgePolicyHandler;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    PolicyAcknowledgmentRepository ackRepository;
    @Mock
    AuditService auditService;

    private PolicyServiceImpl service;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID POLICY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var acknowledgeHandler =
                new AcknowledgePolicyHandler(ackRepository, auditService, List.of());
        service = new PolicyServiceImpl(ackRepository, acknowledgeHandler);
    }

    private void withContext(Runnable action) {
        var ctx = new SessionContext(ORG_ID, USER_ID, "user1", List.of("analyst"), 1);
        ScopedValue.where(SessionContextHolder.SESSION, ctx).run(action);
    }

    @Test
    void acknowledgePolicy_savesAcknowledgmentAndLogsAudit() {
        when(ackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        withContext(() -> {
            var dto = service
                    .acknowledgePolicy(new AcknowledgePolicyCommand(POLICY_ID, "1.0", "10.0.0.1"));

            assertThat(dto.policyRecordId()).isEqualTo(POLICY_ID);
            assertThat(dto.userId()).isEqualTo(USER_ID);
            assertThat(dto.policyVersion()).isEqualTo("1.0");
            assertThat(dto.method()).isEqualTo("platform");
        });

        var captor = ArgumentCaptor.forClass(PolicyAcknowledgment.class);
        verify(ackRepository).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(ORG_ID);
        assertThat(captor.getValue().getIpAddress()).isEqualTo("10.0.0.1");

        var auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).log(auditCaptor.capture());
        assertThat(auditCaptor.getValue().operation()).isEqualTo("POLICY_ACKNOWLEDGED");
    }

    @Test
    void acknowledgePolicy_withBlankVersion_throwsValidationException() {
        withContext(() -> assertThatThrownBy(() -> service
                .acknowledgePolicy(new AcknowledgePolicyCommand(POLICY_ID, "  ", null)))
                        .isInstanceOf(ValidationException.class));
    }

    @Test
    void getAcknowledgments_returnsAllForPolicy() {
        var ack = PolicyAcknowledgment.create(ORG_ID, POLICY_ID, USER_ID, "1.0", "platform", null);
        when(ackRepository.findByOrgIdAndPolicyRecordId(ORG_ID, POLICY_ID))
                .thenReturn(List.of(ack));

        withContext(() -> {
            var results = service.getAcknowledgments(POLICY_ID);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).policyVersion()).isEqualTo("1.0");
        });
    }

    @Test
    void countAcknowledgments_returnsCorrectCount() {
        when(ackRepository.countByOrgIdAndPolicyRecordId(ORG_ID, POLICY_ID)).thenReturn(5L);

        withContext(() -> {
            assertThat(service.countAcknowledgments(POLICY_ID)).isEqualTo(5L);
        });
    }

    @Test
    void getMyAcknowledgments_returnsAcksForCurrentUser() {
        var ack = PolicyAcknowledgment.create(ORG_ID, POLICY_ID, USER_ID, "2.0", "platform", null);
        when(ackRepository.findByOrgIdAndUserId(ORG_ID, USER_ID)).thenReturn(List.of(ack));

        withContext(() -> {
            var results = service.getMyAcknowledgments();
            assertThat(results).hasSize(1);
            assertThat(results.get(0).userId()).isEqualTo(USER_ID);
        });
    }
}
