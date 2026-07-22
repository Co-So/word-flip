package com.wordflip.core.model.learning

import com.wordflip.core.model.study.CardProgress
import com.wordflip.core.model.study.Sense
import com.wordflip.core.model.study.SourceMaterial

/** 当前主词书对应的服务端学习计划。 */
data class LearningPlan(
    val planId: Long,
    val bookId: Long,
    val bookName: String,
    val status: String,
    val dailyNewCardLimit: Int,
    val active: Boolean,
    val createdAt: String,
)

/** 创建并激活一本词书计划。 */
data class CreateLearningPlanRequest(
    val bookId: Long,
    val dailyNewCardLimit: Int = 20,
)

/** 切换历史计划或修改当前计划。 */
data class PatchLearningPlanRequest(
    val planId: Long? = null,
    val dailyNewCardLimit: Int? = null,
    val status: String? = null,
)

/** 词书专属学习卡摘要。 */
data class LearningCard(
    val cardId: Long,
    val lexemeId: Long,
    val bookId: Long,
    val wordKey: String,
    val en: String,
    val phonetic: String? = null,
    val version: Int,
    val senses: List<Sense> = emptyList(),
)

/** 学习卡详情：默认考义、双轨 FSRS 和来源资料位于同一扁平响应。 */
data class LearningCardDetail(
    val cardId: Long,
    val lexemeId: Long,
    val bookId: Long,
    val wordKey: String,
    val en: String,
    val phonetic: String? = null,
    val version: Int,
    val senses: List<Sense> = emptyList(),
    val progress: CardProgress,
    val sourceMaterials: List<SourceMaterial> = emptyList(),
)

/** GET /books/{bookId}/cards 分页响应。 */
data class BookCardsResponse(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val cards: List<LearningCard> = emptyList(),
)
