package com.grcplatform.api.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.grcplatform.core.context.SessionContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-org rate limit: 1 000 requests per org per minute (sliding window via Redis INCR + TTL).
 * Requests without a bound session context (unauthenticated paths) are passed through.
 *
 * Redis key: "rl:{orgId}:{epochMinute}"
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int LIMIT = 1_000;

    private final StringRedisTemplate redisTemplate;

    public RateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!SessionContextHolder.SESSION.isBound()) {
            chain.doFilter(request, response);
            return;
        }

        var orgId = SessionContextHolder.SESSION.get().orgId();
        var minute = Instant.now().truncatedTo(ChronoUnit.MINUTES).getEpochSecond() / 60;
        var key = "rl:" + orgId + ":" + minute;
        var ops = redisTemplate.opsForValue();

        var count = ops.increment(key);
        if (count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(90));
        }

        if (count != null && count > LIMIT) {
            response.setHeader("Retry-After", "60");
            response.sendError(429,
                    "Rate limit exceeded. Max " + LIMIT + " requests per minute per org.");
            return;
        }

        chain.doFilter(request, response);
    }
}
