package com.wordflip.core.model.stats

/**
 * 统计四宫格摘要，对齐 openapi `StatsSummary`（REQ-STATS-1）。
 */
data class StatsSummary(
    val masteredCount: Int,
    val streakDays: Int,
    val quizAccuracy: Float,
    val totalStudyDays: Int,
)

/** 热力图单日，对齐 openapi `StatsHeatmap.days[]`（REQ-STATS-2） */
data class HeatmapDay(
    val date: String,
    val level: Int,
)

/** 成就项，对齐 openapi `AchievementsResponse.items[]`（REQ-STATS-3） */
data class AchievementItem(
    val id: String,
    val name: String,
    val description: String,
    val unlocked: Boolean,
    val unlockedAt: String? = null,
)

/** 统计页聚合 Mock 载荷 */
data class StatsDashboard(
    val summary: StatsSummary,
    val heatmapDays: List<HeatmapDay>,
    val achievements: List<AchievementItem>,
)
