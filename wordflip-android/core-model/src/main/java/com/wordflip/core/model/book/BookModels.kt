package com.wordflip.core.model.book

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
 * 用户词书设置快照，对齐 openapi `UserSettingsResponse` 核心字段。
 */
data class UserSettingsSnapshot(
    val bookIds: List<Long>,
    val groupSize: Int,
    val summary: BooksSummary,
)

/** Mock 保存结果，对齐 openapi `SaveBooksSettingsResponse.appendedGroups` */
data class SaveBooksResult(
    val settings: UserSettingsSnapshot,
    val appendedGroupCount: Int,
)
