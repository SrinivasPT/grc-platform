package com.grcplatform.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the GRC API.
 *
 * Filter execution order (after BearerTokenAuthenticationFilter): 1. JwtFreshnessFilter — JWT age +
 * role_version check 2. IdempotencyFilter — duplicate request detection 3. SessionContextFilter —
 * ScopedValue binding 4. RateLimitFilter — per-org token bucket
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

        private final JwtPrincipalConverter jwtPrincipalConverter;
        private final JwtFreshnessFilter jwtFreshnessFilter;
        private final IdempotencyFilter idempotencyFilter;
        private final SessionContextFilter sessionContextFilter;
        private final RateLimitFilter rateLimitFilter;

        public SecurityConfig(JwtPrincipalConverter jwtPrincipalConverter,
                        JwtFreshnessFilter jwtFreshnessFilter, IdempotencyFilter idempotencyFilter,
                        SessionContextFilter sessionContextFilter,
                        RateLimitFilter rateLimitFilter) {
                this.jwtPrincipalConverter = jwtPrincipalConverter;
                this.jwtFreshnessFilter = jwtFreshnessFilter;
                this.idempotencyFilter = idempotencyFilter;
                this.sessionContextFilter = sessionContextFilter;
                this.rateLimitFilter = rateLimitFilter;
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                return http.csrf(csrf -> csrf.disable()).sessionManagement(
                                sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                                                .jwtAuthenticationConverter(jwtPrincipalConverter)))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/actuator/health",
                                                                "/actuator/info")
                                                .permitAll().anyRequest().authenticated())
                                .addFilterAfter(jwtFreshnessFilter,
                                                BearerTokenAuthenticationFilter.class)
                                .addFilterAfter(idempotencyFilter, JwtFreshnessFilter.class)
                                .addFilterAfter(sessionContextFilter, IdempotencyFilter.class)
                                .addFilterAfter(rateLimitFilter, SessionContextFilter.class)
                                .build();
        }
}
