package com.grcplatform.api.security;

import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import com.grcplatform.core.security.JwtPrincipal;

/**
 * Converts a validated Keycloak JWT into a JwtAuthenticationToken whose principal is JwtPrincipal.
 * Extracts: org_id, sub (user_id), preferred_username, realm_access.roles, rv (role_version).
 */
@Component
public class JwtPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var principal = extractPrincipal(jwt);
        return new JwtAuthenticationToken(jwt, List.of(), principal.username()) {
            @Override
            public Object getPrincipal() {
                return principal;
            }
        };
    }

    private JwtPrincipal extractPrincipal(Jwt jwt) {
        var orgIdStr = jwt.getClaimAsString("org_id");
        var orgId = orgIdStr != null ? UUID.fromString(orgIdStr) : null;
        var userId = UUID.fromString(jwt.getSubject());
        var username = jwt.getClaimAsString("preferred_username");
        var roles = extractRoles(jwt);
        var rv = jwt.getClaim("rv") instanceof Number n ? n.intValue() : 0;

        return JwtPrincipal.of(orgId, userId, username, roles, rv);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        var realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return List.of();
        var roles = (List<String>) realmAccess.get("roles");
        return roles != null ? List.copyOf(roles) : List.of();
    }
}
