package com.grcplatform.api.security;

import com.grcplatform.core.security.JwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtFreshnessFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private FilterChain chain;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private JwtFreshnessFilter filter;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        filter = new JwtFreshnessFilter(redisTemplate);
    }

    @Test
    void validFreshToken_withCurrentRoleVersion_proceedsChain() throws Exception {
        var principal = JwtPrincipal.of(ORG_ID, USER_ID, "alice", List.of("grc-analyst"), 5);
        var jwt = buildJwt(Instant.now().minusSeconds(60), 5);
        var auth = buildAuthentication(principal, jwt);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rvv:" + USER_ID)).thenReturn("5");

        filter.checkFreshness(auth, request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void expiredToken_olderThan5Minutes_returns401() throws Exception {
        var principal = JwtPrincipal.of(ORG_ID, USER_ID, "alice", List.of("grc-analyst"), 5);
        var jwt = buildJwt(Instant.now().minusSeconds(400), 5);
        var auth = buildAuthentication(principal, jwt);

        filter.checkFreshness(auth, request, response, chain);

        verify(response).sendError(eq(401), contains("token_expired"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void staleRoleVersion_inJwt_returns401() throws Exception {
        var principal = JwtPrincipal.of(ORG_ID, USER_ID, "alice", List.of("grc-analyst"), 3);
        var jwt = buildJwt(Instant.now().minusSeconds(60), 3);
        var auth = buildAuthentication(principal, jwt);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rvv:" + USER_ID)).thenReturn("7");

        filter.checkFreshness(auth, request, response, chain);

        verify(response).sendError(eq(401), contains("role_version_stale"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void noRoleVersionInRedis_treatsAsUpToDate_proceedsChain() throws Exception {
        var principal = JwtPrincipal.of(ORG_ID, USER_ID, "alice", List.of("grc-analyst"), 5);
        var jwt = buildJwt(Instant.now().minusSeconds(30), 5);
        var auth = buildAuthentication(principal, jwt);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rvv:" + USER_ID)).thenReturn(null);

        filter.checkFreshness(auth, request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void unauthenticatedRequest_proceedsChainWithoutCheck() throws Exception {
        filter.checkFreshness(null, request, response, chain);
        verify(chain).doFilter(request, response);
    }

    // ---- helpers ----

    private Jwt buildJwt(Instant issuedAt, int roleVersion) {
        return Jwt.withTokenValue("token").header("alg", "RS256").issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(300)).claim("sub", USER_ID.toString())
                .claim("rv", roleVersion).build();
    }

    private org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken buildAuthentication(
            JwtPrincipal principal, Jwt jwt) {
        return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                jwt, List.of(), principal.username());
    }
}
