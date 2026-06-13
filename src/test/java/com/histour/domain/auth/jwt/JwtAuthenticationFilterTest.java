package com.histour.domain.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.histour.domain.auth.security.AuthenticatedUser;
import com.histour.domain.user.dto.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(authenticationManager, jwtProvider, objectMapper);

    @Test
    void attemptAuthenticationDelegatesEmailAndPasswordToAuthenticationManager() {
        // This filter is responsible only for the login request.
        // It reads email/password from JSON and delegates the actual authentication
        // to AuthenticationManager. AuthenticationManager will call AuthService
        // because AuthService implements UserDetailsService.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/json");
        request.setContent("""
                {
                  "email": "user@email.com",
                  "password": "plain-password"
                }
                """.getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        Authentication authenticated = mock(Authentication.class);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);

        Authentication result = filter.attemptAuthentication(request, response);

        // The filter should return exactly what AuthenticationManager returns.
        // Password validation must not happen inside this filter.
        assertThat(result).isSameAs(authenticated);

        // Verify that the JSON body was converted into Spring Security's standard
        // UsernamePasswordAuthenticationToken with email as principal and password
        // as credentials.
        var captor = forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(captor.getValue().getPrincipal()).isEqualTo("user@email.com");
        assertThat(captor.getValue().getCredentials()).isEqualTo("plain-password");
    }

    @Test
    void successfulAuthenticationWritesAccessTokenResponse() throws IOException {
        // successfulAuthentication() runs after Spring Security has already verified
        // email/password. At this point the principal is the AuthenticatedUser returned
        // by AuthService.loadUserByUsername().
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        User user = User.builder()
                .id(1L)
                .email("user@email.com")
                .password("encoded-password")
                .build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(user),
                null
        );
        when(jwtProvider.createAccessToken(1L)).thenReturn("access-token");

        filter.successfulAuthentication(request, response, chain, authentication);

        // The login response should contain only the access token for now.
        // Refresh token behavior is intentionally not covered here yet.
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"accessToken\":\"access-token\"");
    }
}
