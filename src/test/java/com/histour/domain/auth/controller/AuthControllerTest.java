package com.histour.domain.auth.controller;

import com.histour.common.response.ApiResponse;
import com.histour.domain.auth.dto.RefreshTokenRequest;
import com.histour.domain.auth.dto.TokenResponse;
import com.histour.domain.auth.jwt.JwtProvider;
import com.histour.domain.auth.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AuthController authController = new AuthController(jwtProvider, refreshTokenService);

    @Test
    void refreshRotatesRefreshTokenWhenStoredTokenMatches() {
        when(jwtProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtProvider.getUserId("refresh-token")).thenReturn(1L);
        when(refreshTokenService.matches(1L, "refresh-token")).thenReturn(true);
        when(jwtProvider.createAccessToken(1L)).thenReturn("new-access-token");
        when(jwtProvider.createRefreshToken(1L)).thenReturn("new-refresh-token");

        ResponseEntity<ApiResponse<TokenResponse>> response =
                authController.refresh(new RefreshTokenRequest("refresh-token"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().accessToken()).isEqualTo("new-access-token");
        assertThat(response.getBody().getData().refreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenService).save(1L, "new-refresh-token");
    }

    @Test
    void refreshRejectsTokenWhenStoredTokenDoesNotMatch() {
        when(jwtProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtProvider.getUserId("refresh-token")).thenReturn(1L);
        when(refreshTokenService.matches(1L, "refresh-token")).thenReturn(false);

        assertThatThrownBy(() -> authController.refresh(new RefreshTokenRequest("refresh-token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid refresh token.");
    }
}
