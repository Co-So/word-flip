package com.wordflip.core.model.fake

import com.wordflip.core.model.group.GroupDetail
import com.wordflip.core.model.group.GroupSource
import com.wordflip.core.model.group.GroupStats
import com.wordflip.core.model.group.GroupStatus

/**
 * 分组页 Mock 数据；第 3 组与 FakeTodayData / FakeStudyData 对齐（20 词）。
 */
object FakeGroupsData {

    private val groups = listOf(
        GroupDetail(
            id = 1,
            name = "第1组",
            source = GroupSource.AUTO,
            status = GroupStatus.COMPLETED,
            createdAt = "2026-06-01T08:00:00Z",
            stats = GroupStats(unlearned = 2, fuzzy = 1, unknown = 0, total = 20),
            progress = 0.85f,
        ),
        GroupDetail(
            id = 2,
            name = "第2组",
            source = GroupSource.AUTO,
            status = GroupStatus.LEARNING,
            createdAt = "2026-06-10T08:00:00Z",
            stats = GroupStats(unlearned = 8, fuzzy = 5, unknown = 2, total = 20),
            progress = 0.25f,
        ),
        GroupDetail(
            id = 3,
            name = "第3组",
            source = GroupSource.AUTO,
            status = GroupStatus.NOT_STARTED,
            createdAt = "2026-06-15T08:00:00Z",
            stats = GroupStats(unlearned = 20, fuzzy = 0, unknown = 0, total = 20),
            progress = 0f,
        ),
        GroupDetail(
            id = 4,
            name = "第4组",
            source = GroupSource.AUTO,
            status = GroupStatus.NOT_STARTED,
            createdAt = "2026-06-16T08:00:00Z",
            stats = GroupStats(unlearned = 15, fuzzy = 0, unknown = 0, total = 15),
            progress = 0f,
        ),
        GroupDetail(
            id = 5,
            name = "第5组",
            source = GroupSource.AUTO,
            status = GroupStatus.LEARNING,
            createdAt = "2026-06-18T08:00:00Z",
            stats = GroupStats(unlearned = 6, fuzzy = 3, unknown = 1, total = 10),
            progress = 0f,
        ),
        GroupDetail(
            id = 6,
            name = "自定义分组 1",
            source = GroupSource.CUSTOM,
            status = GroupStatus.LEARNING,
            createdAt = "2026-06-20T08:00:00Z",
            stats = GroupStats(unlearned = 4, fuzzy = 2, unknown = 1, total = 10),
            progress = 0.3f,
        ),
    )

    fun list(): List<GroupDetail> = groups

    fun findById(groupId: Int): GroupDetail? = groups.find { it.id == groupId }
}
