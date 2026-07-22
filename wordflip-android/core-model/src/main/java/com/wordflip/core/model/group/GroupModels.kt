package com.wordflip.core.model.group

import com.google.gson.annotations.SerializedName
import com.wordflip.core.model.study.CardProgress
import com.wordflip.core.model.study.Sense
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
 * 分组学习卡列表项；双轨 FSRS 和展示热力均由服务端返回。
 */
data class GroupWordItem(
    val summary: WordSummary,
    val progress: CardProgress,
) {
    /** 组详情只展示服务端计算的热力档。 */
    val displayHeatLevel: Int
        get() = progress.displayHeatLevel
}

/** GET /groups 响应 */
data class GroupListResponse(
    val groups: List<GroupDetail> = emptyList(),
)

/**
 * OpenAPI `GroupCardsResponse.cards[]` 为扁平 LearningCardDetail；
 * Gson 反序列化后用 [toGroupWordItem] 转为 UI 层 [GroupWordItem]。
 */
data class GroupWordItemDto(
    val cardId: Long,
    val lexemeId: Long,
    val bookId: Long,
    val version: Int,
    val wordKey: String,
    val en: String,
    val cn: String? = null,
    val pos: String? = null,
    @SerializedName("phonetic") val ph: String? = null,
    val senses: List<Sense> = emptyList(),
    val progress: CardProgress,
) {
    fun toGroupWordItem(): GroupWordItem = GroupWordItem(
        summary = WordSummary(
            wordKey = wordKey,
            en = en,
            cn = cn.orEmpty(),
            pos = pos,
            ph = ph,
            senses = senses,
            cardId = cardId,
            lexemeId = lexemeId,
            bookId = bookId,
            version = version,
        ),
        progress = progress,
    )
}

/** GET /groups/{groupId}/cards 分页响应 */
data class GroupWordsResponse(
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    @SerializedName("cards") val words: List<GroupWordItemDto> = emptyList(),
)
