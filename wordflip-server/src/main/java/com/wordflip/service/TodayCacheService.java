package com.wordflip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.dto.today.TodayDashboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

/**
 * Today 仪表盘 Redis 缓存：key = today:{userId}:{yyyyMMdd}，TTL 10min（P1-B07 占位）。
 */
@Service
public class TodayCacheService {

    private static final Logger log = LoggerFactory.getLogger(TodayCacheService.class);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TodayCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<TodayDashboard> get(Long userId, LocalDate date) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey(userId, date));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, TodayDashboard.class));
        } catch (Exception ex) {
            log.warn("Today cache read failed, fallback to DB: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(Long userId, LocalDate date, TodayDashboard dashboard) {
        try {
            String json = objectMapper.writeValueAsString(dashboard);
            redisTemplate.opsForValue().set(cacheKey(userId, date), json, TTL);
        } catch (JsonProcessingException ex) {
            log.warn("Today cache write failed: {}", ex.getMessage());
        }
    }

    /** study session / 未来 mastery 变更后失效 */
    public void invalidate(Long userId, LocalDate date) {
        try {
            redisTemplate.delete(cacheKey(userId, date));
        } catch (Exception ex) {
            log.warn("Today cache invalidate failed: {}", ex.getMessage());
        }
    }

    /**
     * regroup / append 后失效该用户全部 Today 缓存（groupId 变更会导致 recommendedStudy 过期）。
     */
    public void invalidateAllForUser(Long userId) {
        try {
            Set<String> keys = redisTemplate.keys(userCachePattern(userId));
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ex) {
            log.warn("Today cache invalidateAllForUser failed: {}", ex.getMessage());
        }
    }

    static String cacheKey(Long userId, LocalDate date) {
        return "today:" + userId + ":" + date.format(KEY_DATE);
    }

    static String userCachePattern(Long userId) {
        return "today:" + userId + ":*";
    }
}
