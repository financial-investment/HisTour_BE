package com.histour.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final int MAX_CALLS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    public void checkExplain(Long userId) {
        String key = KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, WINDOW);
        } else if (Boolean.TRUE.equals(redisTemplate.hasKey(key)) && redisTemplate.getExpire(key) < 0) {
            // 서버 재시작 등으로 EXPIRE가 누락된 TTL 없는 키 복구
            redisTemplate.delete(key);
            redisTemplate.opsForValue().set(key, "1");
            redisTemplate.expire(key, WINDOW);
            count = 1L;
        }

        if (count != null && count > MAX_CALLS) {
            throw new IllegalStateException("해설 요청은 1분에 최대 " + MAX_CALLS + "회까지 가능합니다.");
        }
    }
}
