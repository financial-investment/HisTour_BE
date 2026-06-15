package com.histour.domain.auth.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET_KEY = "test-secret-key-must-be-at-least-32-bytes";

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(
                3_600_000L,
                604_800_000L,
                SECRET_KEY
        );
    }

    @Test
    void createsValidAccessToken() {
        String accessToken = jwtProvider.createAccessToken(1L);

        assertThat(accessToken).isNotBlank();
        assertThat(jwtProvider.validateToken(accessToken)).isTrue();
        assertThat(jwtProvider.getUserId(accessToken)).isEqualTo(1L);
        assertThat(jwtProvider.isAccessToken(accessToken)).isTrue();
        assertThat(jwtProvider.isRefreshToken(accessToken)).isFalse();
    }

    @Test
    void createsValidRefreshToken() {
        String refreshToken = jwtProvider.createRefreshToken(1L);

        assertThat(refreshToken).isNotBlank();
        assertThat(jwtProvider.validateToken(refreshToken)).isTrue();
        assertThat(jwtProvider.getUserId(refreshToken)).isEqualTo(1L);
        assertThat(jwtProvider.isRefreshToken(refreshToken)).isTrue();
        assertThat(jwtProvider.isAccessToken(refreshToken)).isFalse();
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(jwtProvider.validateToken("invalid.token.value")).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        JwtProvider expiredJwtProvider = new JwtProvider(
                -1_000L,
                -1_000L,
                SECRET_KEY
        );
        String expiredToken = expiredJwtProvider.createAccessToken(1L);

        assertThat(jwtProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtProvider otherJwtProvider = new JwtProvider(
                3_600_000L,
                604_800_000L,
                "other-secret-key-must-be-at-least-32-bytes"
        );
        String token = otherJwtProvider.createAccessToken(1L);

        assertThat(jwtProvider.validateToken(token)).isFalse();
    }

}
