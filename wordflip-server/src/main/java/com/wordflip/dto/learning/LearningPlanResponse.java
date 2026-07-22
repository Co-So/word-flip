package com.wordflip.dto.learning;

import java.time.Instant;

/**
 * 用户一本词书的学习计划响应。
 */
public record LearningPlanResponse(
        Long planId,
        Long bookId,
        String bookName,
        String status,
        int dailyNewCardLimit,
        boolean active,
        Instant createdAt
) {
    /**
     * 返回激活后的不可变副本。
     */
    public LearningPlanResponse activated() {
        return new LearningPlanResponse(
                planId, bookId, bookName, status, dailyNewCardLimit, true, createdAt
        );
    }
}
