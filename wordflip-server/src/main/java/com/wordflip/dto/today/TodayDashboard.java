package com.wordflip.dto.today;

import java.time.LocalDate;

/**
 * 今日仪表盘，对齐 openapi TodayDashboard。
 */
public record TodayDashboard(
        LocalDate date,
        int streakDays,
        TodayStats stats,
        TodayTasks tasks,
        RecommendedStudy recommendedStudy
) {
}
