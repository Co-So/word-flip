package com.wordflip.core.model.book

import com.wordflip.core.model.settings.ThemeMode

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
    val autoSpeak: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val summary: BooksSummary,
)

/**
 * PUT /settings 请求体。
 */
data class SaveBooksSettingsRequest(
    val bookIds: List<Long>,
    val groupSize: Int,
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
    val autoSpeak: Boolean,
    val themeMode: ThemeMode,
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
