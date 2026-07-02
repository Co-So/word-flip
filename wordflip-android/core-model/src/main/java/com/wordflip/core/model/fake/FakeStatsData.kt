package com.wordflip.core.model.fake

import com.wordflip.core.model.stats.AchievementItem
import com.wordflip.core.model.stats.HeatmapDay
import com.wordflip.core.model.stats.StatsDashboard
import com.wordflip.core.model.stats.StatsSummary
import java.time.LocalDate
import kotlin.math.abs

/**
 * 统计页 Mock 数据；KPI 与 FakeTodayData 打卡天数对齐，热力图为近 3 个月确定性生成。
 */
object FakeStatsData {

    val dashboard: StatsDashboard
        get() = StatsDashboard(
            summary = StatsSummary(
                masteredCount = 42,
                streakDays = FakeTodayData.dashboard.streakDays,
                quizAccuracy = 0.87f,
                totalStudyDays = 28,
            ),
            heatmapDays = buildHeatmapDays(),
            achievements = listOf(
                AchievementItem(
                    id = "streak_7",
                    name = "坚持一周",
                    description = "连续学习 7 天",
                    unlocked = true,
                    unlockedAt = "2026-06-25",
                ),
                AchievementItem(
                    id = "quiz_80",
                    name = "测验达人",
                    description = "单次测验正确率 ≥ 80%",
                    unlocked = true,
                    unlockedAt = "2026-06-20",
                ),
                AchievementItem(
                    id = "master_100",
                    name = "百词斩",
                    description = "累计掌握 100 个单词",
                    unlocked = false,
                ),
                AchievementItem(
                    id = "book_complete",
                    name = "完成第一本词书",
                    description = "完整学完任意一本词书",
                    unlocked = false,
                ),
            ),
        )

    /** 近 91 天（约 3 个月）activity level 0~3 */
    private fun buildHeatmapDays(): List<HeatmapDay> {
        val end = LocalDate.now()
        val start = end.minusDays(90)
        val days = mutableListOf<HeatmapDay>()
        var date = start
        while (!date.isAfter(end)) {
            val seed = abs(date.toEpochDay().toInt()) % 17
            val level = when {
                seed <= 4 -> 0
                seed <= 8 -> 1
                seed <= 12 -> 2
                else -> 3
            }
            days += HeatmapDay(date = date.toString(), level = level)
            date = date.plusDays(1)
        }
        return days
    }
}
