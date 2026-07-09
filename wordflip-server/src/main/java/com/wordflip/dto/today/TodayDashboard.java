package com.wordflip.dto.today;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日仪表盘，对齐 openapi TodayDashboard。
 */
public record TodayDashboard(
        LocalDate date,
        int streakDays,
        TodayStats stats,
        TodayTasks tasks,
        RecommendedStudy recommendedStudy,
        List<RecentGroupDto> recentGroups
) {
    public TodayDashboard(
            LocalDate date,
            int streakDays,
            TodayStats stats,
            TodayTasks tasks,
            RecommendedStudy recommendedStudy
    ) {
        this(date, streakDays, stats, tasks, recommendedStudy, List.of());
    }
}
