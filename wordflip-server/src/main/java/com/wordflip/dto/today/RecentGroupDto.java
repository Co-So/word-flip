package com.wordflip.dto.today;

import java.time.Instant;

/**
 * 今日页最近学习分组摘要（最多 3 条）。
 */
public record RecentGroupDto(
        long groupId,
        String name,
        Instant lastStudiedAt
) {
}
