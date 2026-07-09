package com.wordflip.core.model.today

/**
 * 今日仪表盘 DTO，字段对齐 openapi.yaml `TodayDashboard`。
 * 后续 OpenAPI Generator 接入后可替换为生成类。
 */
data class TodayDashboard(
    val date: String,
    val streakDays: Int,
    val stats: TodayStats,
    val tasks: TodayTasks,
    val recommendedStudy: RecommendedStudy?,
    /** 最近学习/测验的分组（最多 3），点击可进组测 */
    val recentGroups: List<RecentGroup> = emptyList(),
)

/** 最近学习分组摘要，对齐 openapi `TodayDashboard.recentGroups[]` */
data class RecentGroup(
    val groupId: Int,
    val name: String,
    val lastStudiedAt: String,
)

/** 三格统计：已掌握 / 待复习 / 完成度（REQ-TODAY-3、REQ-TODAY-12） */
data class TodayStats(
    val masteredCount: Int,
    val dueReviewCount: Int,
    val completionPercent: Int,
)

/** 今日三条任务（REQ-TODAY-4、REQ-TODAY-9~11） */
data class TodayTasks(
    val newWords: TodayTask,
    val dueReview: TodayTask,
    val quiz: TodayTask,
)

data class TodayTask(
    val count: Int,
    val label: String,
    val sources: List<TaskSource> = emptyList(),
)

/** 任务来源分组摘要 */
data class TaskSource(
    val groupId: Int,
    val groupName: String,
    val count: Int,
)

/** 推荐学习分组，供底部 CTA 文案（REQ-TODAY-7） */
data class RecommendedStudy(
    val groupId: Int,
    val groupName: String,
    val wordCount: Int,
    val reason: StudyReason,
)

enum class StudyReason {
    NEW_WORDS,
    DUE_REVIEW,
    MIXED,
}
