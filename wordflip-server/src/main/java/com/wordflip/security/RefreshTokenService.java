package com.wordflip.security;

import com.wordflip.config.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * Refresh Token Redis 存储：key = refresh:{userId}:{tokenId}，TTL 7 天。
 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    /** 存储 Refresh Token 会话 */
    public void store(Long userId, String tokenId) {
        Duration ttl = Duration.ofDays(jwtProperties.getRefreshExpirationDays());
        redisTemplate.opsForValue().set(key(userId, tokenId), "1", ttl);
    }

    /** 校验 Refresh Token 是否仍有效（未被吊销） */
    public boolean isValid(Long userId, String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(userId, tokenId)));
    }

    /** 吊销单个 Refresh Token（登出 / 轮换） */
    public void revoke(Long userId, String tokenId) {
        redisTemplate.delete(key(userId, tokenId));
    }

    /** 吊销该用户全部 Refresh Token（全设备登出） */
    public void revokeAll(Long userId) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private static String key(Long userId, String tokenId) {
        return KEY_PREFIX + userId + ":" + tokenId;
    }
}
