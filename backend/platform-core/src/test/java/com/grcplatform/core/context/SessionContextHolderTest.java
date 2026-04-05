package com.grcplatform.core.context;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import main.java.com.grcplatform.core.context.SessionContext;
import main.java.com.grcplatform.core.context.SessionContextHolder;

class SessionContextHolderTest {

    @Test
    void current_whenNotBound_throwsIllegalState() {
        assertThatThrownBy(SessionContextHolder::current)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SessionContext is not bound");
    }

    @Test
    void current_whenBound_returnsContext() {
        SessionContext context = SessionContext.of(
                UUID.randomUUID(), UUID.randomUUID(), "test.user",
                List.of("grc-analyst"), 1);

        ScopedValue.where(SessionContextHolder.SESSION, context).run(() -> {
            SessionContext retrieved = SessionContextHolder.current();
            assertThat(retrieved.orgId()).isEqualTo(context.orgId());
            assertThat(retrieved.username()).isEqualTo("test.user");
            assertThat(retrieved.roles()).containsExactly("grc-analyst");
        });
    }

    @Test
    void sessionContext_roles_areImmutable() {
        SessionContext context = SessionContext.of(
                UUID.randomUUID(), UUID.randomUUID(), "test.user",
                List.of("grc-admin"), 1);

        assertThatThrownBy(() -> context.roles().add("injected-role"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
