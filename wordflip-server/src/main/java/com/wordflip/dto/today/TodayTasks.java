package com.wordflip.dto.today;

/**
 * 今日三条任务，对齐 openapi TodayDashboard.tasks。
 */
public record TodayTasks(TodayTask newWords, TodayTask dueReview, TodayTask quiz) {
}
