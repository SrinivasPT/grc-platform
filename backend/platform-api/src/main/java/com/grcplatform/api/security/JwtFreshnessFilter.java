package com.grcplatform.api.security;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates that the JWT: 1. Was issued no more than 5 minutes ago (prevents token replay beyond
 * session window). 2. Carries a role_version (rv) claim ≥ the cached version stored in Redis.
 *
 * Redis key: "rvv:{userId}" → current role_version integer as string. A missing Redis entry (cache
 * miss) is treated as up-to-date and allowed through.
 */
@Component
public class JwtFreshnessFilter extends OncePerRequestFilter {

    static final int MAX_TOKEN_AGE_SECONDS = 300;
    private static final String RVV_PREFIX = "rvv:";

    private final StringRedisTemplate redisTemplate;

    public JwtFreshnessFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        checkFreshness(auth, request, response, chain);
    }

    void checkFreshness(Authentication auth, HttpServletRequest request,
            HttpServletResponse response, FilterChain chain) throws ServletException, IOException {

        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response);
            return;
        }

        var jwt = jwtAuth.getToken();
        var issuedAt = jwt.getIssuedAt();
        if (issuedAt == null || Instant.now()
                .isAfter(issuedAt.plus(MAX_TOKEN_AGE_SECONDS, ChronoUnit.SECONDS))) {
            response.sendError(401, "token_expired: JWT exceeded maximum age of "
                    + MAX_TOKEN_AGE_SECONDS + " seconds");
            return;
        }

        var sub = jwt.getSubject();
        var jwtRv = jwt.getClaim("rv") instanceof Number n ? n.intValue() : 0;
        var cachedRvStr = redisTemplate.opsForValue().get(RVV_PREFIX + sub);
        if (cachedRvStr != null) {
            var cachedRv = Integer.parseInt(cachedRvStr);
            if (cachedRv > jwtRv) {
                response.sendError(401,
                        "role_version_stale: token rv=" + jwtRv + " but current is " + cachedRv);
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
