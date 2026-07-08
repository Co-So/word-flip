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

/** GET /groups 响应 */
data class GroupListResponse(
    val groups: List<GroupDetail> = emptyList(),
)

/**
 * openapi `GroupWordsResponse.words[]` 为 WordSummary + mastery 扁平 JSON；
 * Gson 反序列化后用 [toGroupWordItem] 转为 UI 层 [GroupWordItem]。
 */
data class GroupWordItemDto(
    val wordKey: String,
    val en: String,
    val cn: String,
    val pos: String? = null,
    val ph: String? = null,
    val mastery: MasterySnapshot,
) {
    fun toGroupWordItem(): GroupWordItem = GroupWordItem(
        summary = WordSummary(wordKey = wordKey, en = en, cn = cn, pos = pos, ph = ph),
        mastery = mastery,
    )
}

/** GET /groups/{groupId}/words 分页响应 */
data class GroupWordsResponse(
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val words: List<GroupWordItemDto> = emptyList(),
)
