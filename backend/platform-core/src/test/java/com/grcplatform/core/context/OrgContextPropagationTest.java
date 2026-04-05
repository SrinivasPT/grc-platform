package com.grcplatform.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

@SuppressWarnings("preview")
class OrgContextPropagationTest {

    @Test
    void scopedValue_isVisibleInSameThread() {
        UUID orgId = UUID.randomUUID();
        SessionContext ctx =
                SessionContext.of(orgId, UUID.randomUUID(), "user", List.of("analyst"), 1);

        ScopedValue.where(SessionContextHolder.SESSION, ctx)
                .run(() -> assertThat(SessionContextHolder.current().orgId()).isEqualTo(orgId));
    }

    @Test
    void scopedValue_isNotVisibleOutsideScope() {
        UUID orgId = UUID.randomUUID();
        SessionContext ctx =
                SessionContext.of(orgId, UUID.randomUUID(), "user", List.of("analyst"), 1);

        ScopedValue.where(SessionContextHolder.SESSION, ctx)
                .run(() -> assertThat(SessionContextHolder.SESSION.isBound()).isTrue());

        // After the scope, it is no longer bound
        assertThat(SessionContextHolder.SESSION.isBound()).isFalse();
    }

    @Test
    void scopedValue_orgId_isIsolatedBetweenScopes() {
        UUID orgId1 = UUID.randomUUID();
        UUID orgId2 = UUID.randomUUID();
        SessionContext ctx1 = SessionContext.of(orgId1, UUID.randomUUID(), "user1", List.of(), 1);
        SessionContext ctx2 = SessionContext.of(orgId2, UUID.randomUUID(), "user2", List.of(), 1);

        AtomicReference<UUID> inner1 = new AtomicReference<>();
        AtomicReference<UUID> inner2 = new AtomicReference<>();

        ScopedValue.where(SessionContextHolder.SESSION, ctx1)
                .run(() -> inner1.set(SessionContextHolder.current().orgId()));
        ScopedValue.where(SessionContextHolder.SESSION, ctx2)
                .run(() -> inner2.set(SessionContextHolder.current().orgId()));

        assertThat(inner1.get()).isEqualTo(orgId1);
        assertThat(inner2.get()).isEqualTo(orgId2);
    }

    /**
     * Demonstrates the correct pattern for propagating context to virtual threads: re-bind the
     * ScopedValue explicitly within the submitted task. In Java 21, ScopedValues are NOT
     * automatically inherited by Thread.ofVirtual() — inheritance requires StructuredTaskScope
     * (preview, JEP 446).
     */
    @Test
    void virtualThread_withExplicitContextBinding_seesOrgId() throws Exception {
        UUID orgId = UUID.randomUUID();
        SessionContext ctx =
                SessionContext.of(orgId, UUID.randomUUID(), "user", List.of("analyst"), 1);
        AtomicReference<UUID> capturedOrgId = new AtomicReference<>();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<?> future = executor.submit((Callable<Void>) () -> {
                ScopedValue.where(SessionContextHolder.SESSION, ctx)
                        .run(() -> capturedOrgId.set(SessionContextHolder.current().orgId()));
                return null;
            });
            future.get();
        } finally {
            executor.shutdown();
        }

        assertThat(capturedOrgId.get()).isEqualTo(orgId);
    }

    @Test
    void sessionContext_roles_areImmutable() {
        SessionContext ctx = SessionContext.of(UUID.randomUUID(), UUID.randomUUID(), "test.user",
                List.of("grc-admin"), 1);

        assertThatThrownBy(() -> ctx.roles().add("injected-role"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

