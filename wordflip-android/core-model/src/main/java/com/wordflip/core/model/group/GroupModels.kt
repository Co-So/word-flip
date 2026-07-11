package com.wordflip.core.model.group

import com.wordflip.core.model.study.MasterySnapshot
import com.wordflip.core.model.study.Sense
import com.wordflip.core.model.study.WordProgressSnapshot
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
 * 分组热力分档统计，对齐 openapi `GroupStats`。
 */
data class GroupStats(
    val heat0: Int = 0,
    val heat1: Int = 0,
    val heat2: Int = 0,
    val heat3: Int = 0,
    val heat4: Int = 0,
    val total: Int = 0,
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
 * 掌握度只读展示，仅测验写入（REQ-GROUP）；热力优先 [progress.displayHeatLevel]。
 */
data class GroupWordItem(
    val summary: WordSummary,
    val mastery: MasterySnapshot,
    val progress: WordProgressSnapshot? = null,
) {
    /** 组详情主展示热力档：优先 progress 聚合值 */
    val displayHeatLevel: Int
        get() = progress?.displayHeatLevel ?: mastery.heatLevel
}

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
    val cn: String? = null,
    val pos: String? = null,
    val ph: String? = null,
    val enGloss: String? = null,
    val senses: List<Sense> = emptyList(),
    val mastery: MasterySnapshot,
    val progress: WordProgressSnapshot? = null,
) {
    fun toGroupWordItem(): GroupWordItem = GroupWordItem(
        summary = WordSummary(
            wordKey = wordKey,
            en = en,
            cn = cn.orEmpty(),
            pos = pos,
            ph = ph,
            enGloss = enGloss,
            senses = senses,
        ),
        mastery = mastery,
        progress = progress,
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
