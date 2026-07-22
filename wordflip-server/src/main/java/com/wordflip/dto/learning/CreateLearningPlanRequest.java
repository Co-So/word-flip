package com.wordflip.dto.learning;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 创建并激活学习计划请求。
 */
public record CreateLearningPlanRequest(
        @NotNull Long bookId,
        @Min(1) @Max(200) Integer dailyNewCardLimit
) {
    public int resolvedDailyNewCardLimit() {
        return dailyNewCardLimit == null ? 20 : dailyNewCardLimit;
    }
}
