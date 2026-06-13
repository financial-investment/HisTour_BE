package com.histour.domain.auth.jwt;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtVerificationFilterTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final JwtVerificationFilter filter = new JwtVerificationFilter(jwtProvider);

    @AfterEach
    void tearDown() {
        // SecurityContextHolder uses ThreadLocal storage.
        // Clearing it prevents authentication state from one test leaking into another.
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthenticationWhenBearerTokenIsValid() throws ServletException, IOException {
        // JwtVerificationFilter is not the login filter.
        // It handles already-issued access tokens from the Authorization header.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(jwtProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtProvider.getUserId("valid-token")).thenReturn(1L);

        filter.doFilter(request, response, filterChain);

        // The current project stores userId itself as the principal.
        // Authorities are empty because roles/permissions are not modeled yet.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void doesNotSetAuthenticationWhenHeaderIsMissing() throws ServletException, IOException {
        // Requests without a Bearer token should pass through without authentication.
        // Later authorization rules can decide whether that is allowed.
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // No header means there is no token to validate.
        verify(jwtProvider, never()).validateToken("valid-token");
    }

    @Test
    void doesNotSetAuthenticationWhenTokenIsInvalid() throws ServletException, IOException {
        // Invalid access tokens should not create an Authentication object.
        // This keeps SecurityContext empty for downstream authorization checks.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(jwtProvider.validateToken("invalid-token")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // If validation fails, userId must not be trusted or extracted.
        verify(jwtProvider, never()).getUserId("invalid-token");
    }
}
