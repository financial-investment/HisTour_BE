package com.histour.domain.auth.controller;

import com.histour.common.response.ApiResponse;
import com.histour.domain.auth.dto.RefreshTokenRequest;
import com.histour.domain.auth.dto.TokenResponse;
import com.histour.domain.auth.jwt.JwtProvider;
import com.histour.domain.auth.service.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtProvider.validateToken(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token.");
        }

        Long userId = jwtProvider.getUserId(refreshToken);
        if (!refreshTokenService.matches(userId, refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token.");
        }

        String newAccessToken = jwtProvider.createAccessToken(userId);
        String newRefreshToken = jwtProvider.createRefreshToken(userId);
        refreshTokenService.save(userId, newRefreshToken);

        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(newAccessToken, newRefreshToken)));
    }
}
