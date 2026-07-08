package com.wordflip.dto.group;

import com.wordflip.domain.GroupSource;
import com.wordflip.domain.GroupStatus;
import com.wordflip.domain.StudyGroup;

import java.time.Instant;

/**
 * 分组详情，对齐 openapi GroupDetail。
 */
public record GroupDetail(
        long id,
        String name,
        GroupSource source,
        GroupStatus status,
        Instant createdAt,
        GroupStats stats,
        float progress
) {

    public static GroupDetail of(StudyGroup group, GroupStats stats, float progress) {
        return new GroupDetail(
                group.getId(),
                group.getName(),
                group.getSource(),
                group.getStatus(),
                group.getCreatedAt(),
                stats,
                progress
        );
    }
}
