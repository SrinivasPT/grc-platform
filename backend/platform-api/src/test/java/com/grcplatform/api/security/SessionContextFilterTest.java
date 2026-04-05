package com.grcplatform.api.security;

import com.grcplatform.core.context.SessionContext;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.security.JwtPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionContextFilterTest {

    @Mock
    private FilterChain chain;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private final SessionContextFilter filter = new SessionContextFilter();

    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void sessionContext_isBoundFromJwtClaims_whileInFilterChain() throws Exception {
        var captured = new AtomicReference<SessionContext>();

        setupSecurityContext(ORG_ID, USER_ID, "bob", List.of("grc-analyst"), 3);
        doAnswer(inv -> {
            captured.set(SessionContextHolder.current());
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().orgId()).isEqualTo(ORG_ID);
        assertThat(captured.get().userId()).isEqualTo(USER_ID);
        assertThat(captured.get().username()).isEqualTo("bob");
        assertThat(captured.get().roles()).containsExactly("grc-analyst");
        assertThat(captured.get().roleVersion()).isEqualTo(3);
    }

    @Test
    void sessionContext_isNotBound_afterFilterChainCompletes() throws Exception {
        setupSecurityContext(ORG_ID, USER_ID, "bob", List.of("grc-analyst"), 3);
        filter.doFilterInternal(request, response, chain);

        assertThat(SessionContextHolder.SESSION.isBound()).isFalse();
    }

    @Test
    void missingOrgIdClaim_returns400() throws Exception {
        setupSecurityContextWithoutOrg(USER_ID, "bob");

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(eq(400), contains("org_id"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void noAuthentication_proceedsChainUnchanged() throws Exception {
        SecurityContextHolder.clearContext();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ---- helpers ----

    private void setupSecurityContext(UUID orgId, UUID userId, String username, List<String> roles,
            int rv) {
        var jwt = Jwt.withTokenValue("tok").header("alg", "RS256").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300)).subject(userId.toString())
                .claim("preferred_username", username).claim("org_id", orgId.toString())
                .claim("rv", rv).claim("realm_access", java.util.Map.of("roles", roles)).build();
        var auth = new JwtAuthenticationToken(jwt, List.of(), username);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setupSecurityContextWithoutOrg(UUID userId, String username) {
        var jwt = Jwt.withTokenValue("tok").header("alg", "RS256").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300)).subject(userId.toString())
                .claim("preferred_username", username).build();
        var auth = new JwtAuthenticationToken(jwt, List.of(), username);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
