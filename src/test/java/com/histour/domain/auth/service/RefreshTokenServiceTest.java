package com.histour.domain.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RefreshTokenService refreshTokenService =
            new RefreshTokenService(redisTemplate, 604_800_000L);

    @Test
    void savesRefreshTokenWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        refreshTokenService.save(1L, "refresh-token");

        verify(valueOperations).set(
                "refresh-token:1",
                "refresh-token",
                Duration.ofMillis(604_800_000L)
        );
    }

    @Test
    void returnsTrueWhenSavedTokenMatches() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh-token:1")).thenReturn("refresh-token");

        boolean matches = refreshTokenService.matches(1L, "refresh-token");

        assertThat(matches).isTrue();
    }

    @Test
    void returnsFalseWhenSavedTokenDoesNotMatch() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh-token:1")).thenReturn("other-refresh-token");

        boolean matches = refreshTokenService.matches(1L, "refresh-token");

        assertThat(matches).isFalse();
    }

    @Test
    void deletesRefreshToken() {
        refreshTokenService.delete(1L);

        verify(redisTemplate).delete("refresh-token:1");
    }
}
