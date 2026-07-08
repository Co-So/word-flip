package com.wordflip.dto.today;

/**
 * 今日三格统计，对齐 openapi TodayDashboard.stats。
 */
public record TodayStats(int masteredCount, int dueReviewCount, int completionPercent) {
}
