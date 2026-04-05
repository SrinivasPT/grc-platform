package com.grcplatform.api.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.grcplatform.core.context.SessionContext;
import com.grcplatform.core.context.SessionContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Extracts the org_id, user_id, username, roles, and role_version from the validated JWT and binds
 * them as a {@link SessionContext} on {@link SessionContextHolder#SESSION} for the duration of the
 * request. All downstream code (service layer, repositories) reads context via the ScopedValue —
 * never via method parameters.
 *
 * If the JWT carries no org_id claim the request is rejected with HTTP 400.
 */
@Component
public class SessionContextFilter extends OncePerRequestFilter {

    @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response);
            return;
        }

        var jwt = jwtAuth.getToken();
        var orgIdStr = jwt.getClaimAsString("org_id");
        if (orgIdStr == null || orgIdStr.isBlank()) {
            response.sendError(400, "org_id claim is required but missing from JWT");
            return;
        }

        var ctx = buildContext(jwt, orgIdStr);
        try {
            ScopedValue.where(SessionContextHolder.SESSION, ctx).call(() -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException("Unexpected error in SessionContextFilter", e);
        }
    }

    @SuppressWarnings("unchecked")
    private SessionContext buildContext(org.springframework.security.oauth2.jwt.Jwt jwt,
            String orgIdStr) {
        var orgId = UUID.fromString(orgIdStr);
        var userId = UUID.fromString(jwt.getSubject());
        var username = jwt.getClaimAsString("preferred_username");
        var rv = jwt.getClaim("rv") instanceof Number n ? n.intValue() : 0;

        var realmAccess = jwt.getClaimAsMap("realm_access");
        List<String> roles = List.of();
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> rawRoles) {
            roles = rawRoles.stream().filter(String.class::isInstance).map(String.class::cast)
                    .toList();
        }
        return SessionContext.of(orgId, userId, username != null ? username : userId.toString(),
                roles, rv);
    }
}
