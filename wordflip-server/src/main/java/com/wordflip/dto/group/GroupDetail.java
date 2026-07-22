package com.wordflip.dto.group;

import java.time.Instant;

/**
 * 当前学习计划内的分组详情。
 */
public record GroupDetail(
        long id,
        String name,
        String source,
        String status,
        Instant createdAt,
        GroupStats stats,
        float progress
) {
}
