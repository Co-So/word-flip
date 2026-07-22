package com.wordflip.dto.learning;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 切换或调整当前学习计划请求。
 */
public record PatchLearningPlanRequest(
        Long planId,
        @Min(1) @Max(200) Integer dailyNewCardLimit,
        String status
) {
}
