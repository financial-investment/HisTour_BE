package com.histour.domain.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Service
public class RefreshTokenService {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh-token:";

    private final RedisTemplate<String, String> redisTemplate;
    private final long refreshTokenExpiry;

    public RefreshTokenService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry
    ) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                key(userId),
                refreshToken,
                Duration.ofMillis(refreshTokenExpiry)
        );
    }

    public boolean matches(Long userId, String refreshToken) {
        String savedRefreshToken = redisTemplate.opsForValue().get(key(userId));
        return Objects.equals(savedRefreshToken, refreshToken);
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return REFRESH_TOKEN_KEY_PREFIX + userId;
    }
}
