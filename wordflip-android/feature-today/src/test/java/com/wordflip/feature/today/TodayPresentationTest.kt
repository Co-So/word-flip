package com.wordflip.feature.today

import com.wordflip.core.model.today.RecentGroup
import com.wordflip.core.model.today.RecommendedStudy
import com.wordflip.core.model.today.StudyReason
import com.wordflip.core.model.today.TodayDashboard
import com.wordflip.core.model.today.TodayStats
import com.wordflip.core.model.today.TodayTask
import com.wordflip.core.model.today.TodayTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayPresentationTest {
    @Test
    fun `推荐学习优先于最近分组`() {
        val dashboard = dashboard(
            recommended = RecommendedStudy(7, "推荐组", 24, StudyReason.NEW_WORDS),
            recent = listOf(RecentGroup(8, "最近组", "2026-07-18T08:00:00Z")),
        )

        assertTrue(resolveTodayPrimaryCard(dashboard) is TodayPrimaryCard.Recommended)
    }

    @Test
    fun `无推荐时使用第一条最近分组`() {
        val dashboard = dashboard(
            recommended = null,
            recent = listOf(RecentGroup(8, "最近组", "2026-07-18T08:00:00Z")),
        )

        assertEquals(
            8,
            (resolveTodayPrimaryCard(dashboard) as TodayPrimaryCard.Recent).group.groupId,
        )
    }

    @Test
    fun `推荐与最近都为空时返回空态`() {
        assertEquals(
            TodayPrimaryCard.Empty,
            resolveTodayPrimaryCard(dashboard(recommended = null, recent = emptyList())),
        )
    }

    /** 构造包含零值统计与任务的完整今日仪表盘。 */
    private fun dashboard(
        recommended: RecommendedStudy?,
        recent: List<RecentGroup>,
    ): TodayDashboard {
        val emptyTask = TodayTask(count = 0, label = "")
        return TodayDashboard(
            date = "2026-07-18",
            streakDays = 0,
            stats = TodayStats(
                masteredCount = 0,
                dueReviewCount = 0,
                completionPercent = 0,
            ),
            tasks = TodayTasks(
                newWords = emptyTask,
                dueReview = emptyTask,
                quiz = emptyTask,
            ),
            recommendedStudy = recommended,
            recentGroups = recent,
        )
    }
}
