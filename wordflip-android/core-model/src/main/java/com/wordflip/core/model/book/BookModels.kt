package com.wordflip.core.model.book

import com.wordflip.core.model.settings.ThemeMode

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
data class BooksSummary(
    val distinctSelectedCount: Int,
    val estimatedGroupCount: Int,
    val unassignedCount: Int,
)

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
    val bookIds: List<Long>,
    val groupSize: Int,
    val groupStrategy: GroupStrategy = GroupStrategy.BOOK_ORDER,
    val autoSpeak: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val heatDisplayMode: String = "combined",
    val quizLaunchMode: String = "mixed",
    val defaultQuestionLimit: Int = 10,
    val activeDictId: String = "wordflip_curated",
    val summary: BooksSummary,
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
    val activeDictId: String? = null,
)

/**
 * GET /dicts 词典目录项。
 */
data class DictionaryItem(
    val id: String,
    val name: String,
    val locale: String,
    val licenseNote: String? = null,
    val builtin: Boolean = true,
    val sortOrder: Int = 0,
)

/**
 * PUT /settings 请求体。
 */
data class SaveBooksSettingsRequest(
    val bookIds: List<Long>,
    val groupSize: Int,
    val groupStrategy: GroupStrategy = GroupStrategy.BOOK_ORDER,
    /** true 时重建 auto 分组（REQ-BOOK-26） */
    val regroup: Boolean = false,
)

/**
 * 增量追加分组摘要。
 */
data class AppendedGroups(
    val count: Int,
    val groups: List<AppendedGroupItem>? = null,
)

data class AppendedGroupItem(
    val id: Long,
    val name: String,
    val wordCount: Int,
)

/**
 * PUT /settings 响应（扁平 JSON：settings 字段 + appendedGroups）。
 */
data class SaveBooksSettingsResponse(
    val bookIds: List<Long>,
    val groupSize: Int,
    val groupStrategy: GroupStrategy = GroupStrategy.BOOK_ORDER,
    val autoSpeak: Boolean,
    val themeMode: ThemeMode,
    val heatDisplayMode: String = "combined",
    val quizLaunchMode: String = "mixed",
    val defaultQuestionLimit: Int = 10,
    val summary: BooksSummary,
    val appendedGroups: AppendedGroups,
)

/** 词书页聚合数据 */
data class BooksPageData(
    val books: List<BookItem>,
    val settings: UserSettingsResponse,
)

/** 保存结果摘要 */
data class SaveBooksResult(
    val settings: UserSettingsResponse,
    val appendedGroupCount: Int,
)

/** 词书词条分页，对齐 openapi `BookWordsResponse` */
data class BookWordsResponse(
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val words: List<com.wordflip.core.model.study.WordSummary> = emptyList(),
)
