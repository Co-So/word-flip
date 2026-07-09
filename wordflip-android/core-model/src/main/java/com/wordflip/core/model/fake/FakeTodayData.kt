package com.wordflip.core.model.fake

import com.wordflip.core.model.today.RecentGroup
import com.wordflip.core.model.today.RecommendedStudy
import com.wordflip.core.model.today.StudyReason
import com.wordflip.core.model.today.TaskSource
import com.wordflip.core.model.today.TodayDashboard
import com.wordflip.core.model.today.TodayStats
import com.wordflip.core.model.today.TodayTask
import com.wordflip.core.model.today.TodayTasks
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 今日页 Mock 数据，供 UI 先行开发；数值与 CTA 示例「开始学习 · 第3组 · 20 词」一致。
 */
object FakeTodayData {

    val dashboard: TodayDashboard
        get() = TodayDashboard(
            date = LocalDate.now().toString(),
            streakDays = 7,
            stats = TodayStats(
                masteredCount = 42,
                dueReviewCount = 15,
                completionPercent = 28,
            ),
            tasks = TodayTasks(
                newWords = TodayTask(
                    count = 20,
                    label = "新词学习",
                    sources = listOf(
                        TaskSource(groupId = 3, groupName = "第3组", count = 20),
                    ),
                ),
                dueReview = TodayTask(
                    count = 15,
                    label = "到期复习",
                    sources = listOf(
                        TaskSource(groupId = 2, groupName = "第2组", count = 8),
                        TaskSource(groupId = 3, groupName = "第3组", count = 7),
                    ),
                ),
                quiz = TodayTask(
                    count = 18,
                    label = "默写测验",
                    sources = emptyList(),
                ),
            ),
            recommendedStudy = RecommendedStudy(
                groupId = 3,
                groupName = "第3组",
                wordCount = 20,
                reason = StudyReason.NEW_WORDS,
            ),
            recentGroups = listOf(
                RecentGroup(
                    groupId = 3,
                    name = "第3组",
                    lastStudiedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).toString(),
                ),
                RecentGroup(
                    groupId = 2,
                    name = "第2组",
                    lastStudiedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString(),
                ),
                RecentGroup(
                    groupId = 1,
                    name = "第1组",
                    lastStudiedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2).toString(),
                ),
            ),
        )
}
