package com.wordflip.dto.stats;

/**
 * 当前计划统计摘要。
 */
public record StatsSummaryResponse(
        int masteredCount,
        int streakDays,
        double quizAccuracy,
        int totalStudyDays
) {
}
