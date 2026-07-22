package com.wordflip.dto.study;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * 浏览学习会话上报；该请求只写学习日志，不改变记忆状态。
 */
public record StudySessionReportRequest(
        @NotNull Long groupId,
        Integer durationSec,
        Integer cardsViewed,
        Instant completedAt
) {
}
