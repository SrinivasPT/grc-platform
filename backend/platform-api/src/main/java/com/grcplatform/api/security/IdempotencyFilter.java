package com.grcplatform.api.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.domain.IdempotencyKey;
import com.grcplatform.core.repository.IdempotencyKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Idempotency filter for all state-changing API calls (POST, PUT, PATCH, DELETE). GET requests are
 * passed through without any idempotency check.
 *
 * Clients must include X-Idempotency-Key on every mutating request. Key hash: SHA-256(orgId + ":" +
 * X-Idempotency-Key), stored in idempotency_keys table.
 *
 * On a duplicate request (key already stored and not expired): returns the cached response. On a
 * new request: proceeds, then stores (statusCode, responseBody) for 24 hours.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public IdempotencyFilter(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        var orgId = resolveOrgId();
        doFilterWithOrgId(request, response, chain, orgId);
    }

    void doFilterWithOrgId(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain, UUID orgId) throws ServletException, IOException {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        var rawKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            response.sendError(400, "X-Idempotency-Key header is required for mutating requests");
            return;
        }

        var keyHash = hash(orgId + ":" + rawKey);
        var existing = idempotencyKeyRepository.findByKeyHashIfNotExpired(keyHash, Instant.now());
        if (existing.isPresent()) {
            var cached = existing.get();
            response.setStatus(cached.getStatusCode());
            response.setContentType("application/json");
            response.setHeader("X-Idempotency-Replayed", "true");
            response.getWriter().write(cached.getResponseBody());
            return;
        }

        var wrapped = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapped);
        var body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
        wrapped.copyBodyToResponse();

        var key = IdempotencyKey.of(keyHash, orgId, body, wrapped.getStatus(),
                Instant.now().plus(TTL));
        try {
            idempotencyKeyRepository.save(key);
        } catch (Exception e) {
            log.warn("Failed to persist idempotency key {}: {}", keyHash, e.getMessage());
        }
    }

    private UUID resolveOrgId() {
        if (SessionContextHolder.SESSION.isBound()) {
            return SessionContextHolder.SESSION.get().orgId();
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    private String hash(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }
}
