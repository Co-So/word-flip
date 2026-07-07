package com.wordflip.core.model.fake

import com.wordflip.core.model.book.BookImportConfirmResponse
import com.wordflip.core.model.book.BookImportPreviewResponse
import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BookSource
import com.wordflip.core.model.book.BooksSummary
import com.wordflip.core.model.book.ParsedBookImport
import com.wordflip.core.model.book.SaveBooksResult
import com.wordflip.core.model.book.UserSettingsSnapshot
import java.util.UUID
import kotlin.math.ceil

/**
 * 词书页 Mock 数据；内置 3 本 + 1 本 imported，默认勾选 2 本内置。
 */
object FakeBooksData {

    private const val DEFAULT_GROUP_SIZE = 20

    private var nextBookId = 102L

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

    /** 导入 previewToken → 解析结果暂存（Mock 会话内有效） */
    private val importPreviewCache = mutableMapOf<String, ParsedBookImport>()

    /** 上次持久化（Mock）的勾选与 groupSize，用于模拟 append delta */
    private var savedBookIds: Set<Long> = setOf(1L, 2L)
    private var savedGroupSize: Int = DEFAULT_GROUP_SIZE

    fun books(): List<BookItem> = allBooks.toList()

    fun defaultGroupSize(): Int = DEFAULT_GROUP_SIZE

    fun savedSettings(): UserSettingsSnapshot = buildSettings(savedBookIds, savedGroupSize)

    /**
     * 按勾选词书估算汇总；unassignedCount 来自 FakeUnassignedWordsData 实时池。
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
        val unassigned = if (selectedIds.isEmpty()) {
            0
        } else {
            FakeUnassignedWordsData.unassignedCount()
        }
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

        // 模拟增量 append 后未入组池减少
        FakeUnassignedWordsData.markAssignedForDelta(deltaWords)

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

    /** Mock POST /books/import/preview：缓存解析结果并返回 previewToken */
    fun previewImport(parsed: ParsedBookImport): BookImportPreviewResponse {
        val token = UUID.randomUUID().toString()
        importPreviewCache[token] = parsed
        return BookImportPreviewResponse(
            previewToken = token,
            suggestedName = parsed.suggestedName,
            totalCount = parsed.words.size,
            deduplicatedCount = parsed.deduplicatedCount.takeIf { it > 0 },
            previewWords = parsed.words.take(6),
        )
    }

    /** Mock POST /books/import：写入 imported 词书并自动勾选（REQ-BOOK-8） */
    fun confirmImport(previewToken: String, name: String): BookImportConfirmResponse? {
        val parsed = importPreviewCache.remove(previewToken) ?: return null
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return null
        if (allBooks.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            return null
        }
        val bookId = nextBookId++
        val book = BookItem(
            id = bookId,
            name = trimmedName,
            source = BookSource.IMPORTED,
            wordCount = parsed.words.size,
            declaredCount = null,
            selected = true,
            canDelete = true,
        )
        allBooks.add(book)
        savedBookIds = savedBookIds + bookId
        FakeUnassignedWordsData.appendImportedWords(parsed.words)
        return BookImportConfirmResponse(book = book)
    }

    fun cancelImportPreview(previewToken: String) {
        importPreviewCache.remove(previewToken)
    }
}
