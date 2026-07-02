package com.wordflip.core.model.group

import com.wordflip.core.model.study.MasterySnapshot
import com.wordflip.core.model.study.WordSummary

/** 分组来源，对齐 openapi `GroupDetail.source` */
enum class GroupSource {
    AUTO,
    CUSTOM,
}

/** 分组学习状态，对齐 openapi `GroupDetail.status` */
enum class GroupStatus {
    NOT_STARTED,
    LEARNING,
    COMPLETED,
}

/**
 * 分组四维统计，对齐 openapi `GroupStats`。
 */
data class GroupStats(
    val unlearned: Int,
    val fuzzy: Int,
    val unknown: Int,
    val total: Int,
)

/**
 * 分组详情，对齐 openapi `GroupDetail`。
 */
data class GroupDetail(
    val id: Int,
    val name: String,
    val source: GroupSource,
    val status: GroupStatus,
    val createdAt: String? = null,
    val stats: GroupStats,
    val progress: Float,
)

/**
 * 分组单词列表项，对齐 openapi `GroupWordsResponse.words[]`。
 * 掌握度只读展示，仅测验写入（REQ-GROUP）。
 */
data class GroupWordItem(
    val summary: WordSummary,
    val mastery: MasterySnapshot,
)
