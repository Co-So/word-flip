package com.wordflip.core.model.fake

import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BookSource
import com.wordflip.core.model.book.BooksSummary
import com.wordflip.core.model.book.SaveBooksResult
import com.wordflip.core.model.book.UserSettingsSnapshot
import kotlin.math.ceil

/**
 * 词书页 Mock 数据；内置 3 本 + 1 本 imported，默认勾选 2 本内置。
 */
object FakeBooksData {

    private const val DEFAULT_GROUP_SIZE = 20

    /** 已入组词数 Mock 基准，用于 unassignedCount 估算 */
    private const val MOCK_ASSIGNED_WORD_COUNT = 80

    private val allBooks = mutableListOf(
        BookItem(
            id = 1L,
            name = "四级核心词汇",
            source = BookSource.BUILTIN,
            wordCount = 1200,
            declaredCount = 1200,
            selected = true,
            canDelete = false,
        ),
        BookItem(
            id = 2L,
            name = "六级高频词汇",
            source = BookSource.BUILTIN,
            wordCount = 800,
            declaredCount = 800,
            selected = true,
            canDelete = false,
        ),
        BookItem(
            id = 3L,
            name = "考研英语词汇",
            source = BookSource.BUILTIN,
            wordCount = 5500,
            declaredCount = 5500,
            selected = false,
            canDelete = false,
        ),
        BookItem(
            id = 101L,
            name = "我的生词本",
            source = BookSource.IMPORTED,
            wordCount = 45,
            declaredCount = null,
            selected = false,
            canDelete = true,
        ),
    )

    /** 上次持久化（Mock）的勾选与 groupSize，用于模拟 append delta */
    private var savedBookIds: Set<Long> = setOf(1L, 2L)
    private var savedGroupSize: Int = DEFAULT_GROUP_SIZE

    fun books(): List<BookItem> = allBooks.toList()

    fun defaultGroupSize(): Int = DEFAULT_GROUP_SIZE

    fun savedSettings(): UserSettingsSnapshot = buildSettings(savedBookIds, savedGroupSize)

    /**
     * 按勾选词书去重估算汇总（Mock 简化：各书 wordCount 直接相加，不做跨书去重）。
     */
    fun computeSummary(selectedIds: Set<Long>, groupSize: Int): BooksSummary {
        val distinctCount = allBooks
            .filter { it.id in selectedIds }
            .sumOf { it.wordCount }
        val estimatedGroups = if (groupSize > 0 && distinctCount > 0) {
            ceil(distinctCount.toDouble() / groupSize).toInt()
        } else {
            0
        }
        val unassigned = (distinctCount - MOCK_ASSIGNED_WORD_COUNT).coerceAtLeast(0)
        return BooksSummary(
            distinctSelectedCount = distinctCount,
            estimatedGroupCount = estimatedGroups,
            unassignedCount = unassigned,
        )
    }

    fun buildSettings(selectedIds: Set<Long>, groupSize: Int): UserSettingsSnapshot {
        return UserSettingsSnapshot(
            bookIds = selectedIds.toList().sorted(),
            groupSize = groupSize,
            summary = computeSummary(selectedIds, groupSize),
        )
    }

    /**
     * 模拟 PUT /settings + appendGroupsForNewWords（REQ-BOOK-17~21）。
     * delta 非空时返回新增组数估算；幂等：相同 body 重复保存 appendedGroupCount=0。
     */
    fun saveSettings(selectedIds: Set<Long>, groupSize: Int): SaveBooksResult {
        val previousDistinct = computeSummary(savedBookIds, savedGroupSize).distinctSelectedCount
        val newDistinct = computeSummary(selectedIds, groupSize).distinctSelectedCount
        val deltaWords = (newDistinct - previousDistinct).coerceAtLeast(0)

        val appendedCount = when {
            selectedIds == savedBookIds && groupSize == savedGroupSize -> 0
            deltaWords == 0 -> 0
            groupSize <= 0 -> 0
            else -> ceil(deltaWords.toDouble() / groupSize).toInt().coerceAtLeast(1)
        }

        // 同步勾选态到 Mock 列表
        allBooks.indices.forEach { index ->
            val book = allBooks[index]
            allBooks[index] = book.copy(selected = book.id in selectedIds)
        }
        savedBookIds = selectedIds.toSet()
        savedGroupSize = groupSize

        return SaveBooksResult(
            settings = buildSettings(selectedIds, groupSize),
            appendedGroupCount = appendedCount,
        )
    }

    /** 删除 imported 词书；已入组词保留（REQ-BOOK-20） */
    fun deleteBook(bookId: Long): Boolean {
        val index = allBooks.indexOfFirst { it.id == bookId && it.canDelete }
        if (index < 0) return false
        allBooks.removeAt(index)
        savedBookIds = savedBookIds - bookId
        return true
    }
}
