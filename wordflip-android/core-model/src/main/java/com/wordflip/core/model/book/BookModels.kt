package com.wordflip.core.model.book

import com.wordflip.core.model.settings.ThemeMode
import com.wordflip.core.model.learning.LearningPlan

/**
 * 自动分组策略，对齐 openapi `GroupStrategy`（REQ-BOOK-22~24）。
 */
enum class GroupStrategy {
    BOOK_ORDER,
    FREQUENCY,
    RANDOM,
}

fun GroupStrategy.storageValue(): String = when (this) {
    GroupStrategy.BOOK_ORDER -> "book_order"
    GroupStrategy.FREQUENCY -> "frequency"
    GroupStrategy.RANDOM -> "random"
}

fun parseGroupStrategy(value: String): GroupStrategy = when (value.lowercase()) {
    "frequency" -> GroupStrategy.FREQUENCY
    "random" -> GroupStrategy.RANDOM
    else -> GroupStrategy.BOOK_ORDER
}

/**
 * 词书项，对齐 openapi `BookItem`。
 */
data class BookItem(
    val id: Long,
    val name: String,
    val source: BookSource,
    val wordCount: Int,
    val declaredCount: Int? = null,
    val selected: Boolean,
    val canDelete: Boolean,
)

enum class BookSource {
    BUILTIN,
    IMPORTED,
}

/**
 * 词书汇总，对齐 openapi `BooksSummary`。
 */
/**
 * GET /books 响应。
 */
data class BookListResponse(
    val books: List<BookItem>,
)

/**
 * 用户词书设置，对齐 openapi `UserSettingsResponse`。
 */
data class UserSettingsResponse(
    val activePlanId: Long? = null,
    val groupSize: Int,
    val groupStrategy: GroupStrategy = GroupStrategy.BOOK_ORDER,
    val autoSpeak: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val heatDisplayMode: String = "combined",
    val quizLaunchMode: String = "mixed",
    val defaultQuestionLimit: Int = 10,
)

/**
 * PATCH /settings/preferences 请求体（不触发分组追加）。
 */
data class PreferencesPatchRequest(
    val autoSpeak: Boolean? = null,
    val themeMode: String? = null,
    val heatDisplayMode: String? = null,
    val quizLaunchMode: String? = null,
    val defaultQuestionLimit: Int? = null,
)

/** 历史详情代码的来源目录值对象；不再对应全局选择或独立 API。 */
data class DictionaryItem(
    val id: String,
    val name: String,
    val locale: String,
    val licenseNote: String? = null,
    val builtin: Boolean = true,
    val sortOrder: Int = 0,
)

/** 词书页聚合数据 */
data class BooksPageData(
    val books: List<BookItem>,
    val currentPlan: LearningPlan?,
)

/** 词书学习卡分页在详情页使用的轻量模型。 */
data class BookCardsPage(
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val cards: List<com.wordflip.core.model.study.WordSummary> = emptyList(),
)
