package com.grcplatform.api.security;

import com.grcplatform.core.domain.IdempotencyKey;
import com.grcplatform.core.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private FilterChain chain;
    @Mock
    private HttpServletRequest request;

    private final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new IdempotencyFilter(idempotencyKeyRepository);
    }

    @Test
    void newIdempotencyKey_proceedsChainAndStoresResponse() throws Exception {
        var response = new MockHttpServletResponse();
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Idempotency-Key")).thenReturn("key-123");
        when(idempotencyKeyRepository.findByKeyHashIfNotExpired(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        filter.doFilterWithOrgId(request, response, chain, ORG_ID);

        verify(chain).doFilter(eq(request), any());
        var captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencyKeyRepository).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(ORG_ID);
    }

    @Test
    void duplicateIdempotencyKey_returnsCachedResponseWithoutCallingChain() throws Exception {
        var response = new MockHttpServletResponse();
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Idempotency-Key")).thenReturn("key-123");
        var cached = IdempotencyKey.of("hash", ORG_ID, "{\"id\":\"abc\"}", 200,
                Instant.now().plusSeconds(3600));
        when(idempotencyKeyRepository.findByKeyHashIfNotExpired(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(cached));

        filter.doFilterWithOrgId(request, response, chain, ORG_ID);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"abc\"}");
    }

    @Test
    void getRequest_isSkippedByFilter() throws Exception {
        var response = new MockHttpServletResponse();
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterWithOrgId(request, response, chain, ORG_ID);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(idempotencyKeyRepository);
    }

    @Test
    void postWithoutIdempotencyKeyHeader_returns400() throws Exception {
        var response = new MockHttpServletResponse();
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Idempotency-Key")).thenReturn(null);

        filter.doFilterWithOrgId(request, response, chain, ORG_ID);

        assertThat(response.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(any(), any());
    }
}
