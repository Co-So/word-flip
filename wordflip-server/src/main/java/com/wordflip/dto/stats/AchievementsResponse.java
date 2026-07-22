package com.wordflip.dto.stats;

import java.time.Instant;
import java.util.List;

/**
 * 当前计划成就列表。
 */
public record AchievementsResponse(List<Item> items) {

    public record Item(String id, String name, String description, boolean unlocked, Instant unlockedAt) {
    }
}
