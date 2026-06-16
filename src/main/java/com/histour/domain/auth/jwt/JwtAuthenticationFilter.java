package com.histour.domain.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.histour.common.response.ApiResponse;
import com.histour.domain.auth.dto.LoginRequest;
import com.histour.domain.auth.dto.TokenResponse;
import com.histour.domain.auth.security.AuthenticatedUser;
import com.histour.domain.auth.service.RefreshTokenService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.Set;

public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final String LOGIN_URL = "/api/auth/login";

    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public JwtAuthenticationFilter(
            AuthenticationManager authenticationManager,
            JwtProvider jwtProvider,
            RefreshTokenService refreshTokenService,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        setAuthenticationManager(authenticationManager);
        setFilterProcessesUrl(LOGIN_URL);
    }

    @Override
    public Authentication attemptAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws AuthenticationException {
        try {
            LoginRequest loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
            validateLoginRequest(loginRequest);
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password());
            setDetails(request, authenticationToken);
            return getAuthenticationManager().authenticate(authenticationToken);
        } catch (IOException e) {
            throw new AuthenticationServiceException("Invalid login request.", e);
        }
    }

    private void validateLoginRequest(LoginRequest loginRequest) {
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);
        if (!violations.isEmpty()) {
            throw new AuthenticationServiceException(
                    violations.iterator().next().getMessage()
            );
        }
    }

    @Override
    protected void successfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            Authentication authResult
    ) throws IOException {
        AuthenticatedUser user = (AuthenticatedUser) authResult.getPrincipal();
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        refreshTokenService.save(user.getId(), refreshToken);

        writeJson(response, HttpStatus.OK, ApiResponse.ok(new TokenResponse(accessToken, refreshToken)));
    }

    @Override
    protected void unsuccessfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failed
    ) throws IOException {
        writeJson(response, HttpStatus.UNAUTHORIZED, ApiResponse.error("Invalid email or password."));
    }

    private void writeJson(
            HttpServletResponse response,
            HttpStatus status,
            ApiResponse<?> body
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
