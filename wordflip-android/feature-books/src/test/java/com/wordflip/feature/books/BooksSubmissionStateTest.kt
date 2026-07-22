package com.wordflip.feature.books

import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BooksSubmissionStateTest {
    @Test
    fun `详情成功回传时先消费信号并刷新词书`() {
        val actions = mutableListOf<String>()

        handleBookDetailJoinReturn(
            bookId = 42L,
            onConsumed = { actions += "consume" },
            onRefresh = { actions += "refresh" },
        )

        assertEquals(listOf("consume", "refresh"), actions)
    }

    @Test
    fun `详情回传为空时不消费也不刷新`() {
        val actions = mutableListOf<String>()

        handleBookDetailJoinReturn(
            bookId = null,
            onConsumed = { actions += "consume" },
            onRefresh = { actions += "refresh" },
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `详情首次加入学习时同步占用提交态`() {
        val state = detailContent()

        val result = prepareBookDetailJoinSubmission(state)

        assertEquals(state.copy(isJoiningLearning = true), result)
    }

    @Test
    fun `详情已有加入提交时拒绝再次提交`() {
        val state = detailContent().copy(isJoiningLearning = true)

        assertNull(prepareBookDetailJoinSubmission(state))
    }

    @Test
    fun `导入首次确认时同步占用确认态`() {
        val state = booksContent(importSheet = importSheet())

        val result = prepareImportConfirmation(state)

        assertTrue(result?.importSheet?.isConfirming == true)
    }

    @Test
    fun `导入已有确认提交时拒绝再次提交`() {
        val state = booksContent(importSheet = importSheet().copy(isConfirming = true))

        assertNull(prepareImportConfirmation(state))
    }

    @Test
    fun `导入失败时恢复编辑并保留错误文案`() {
        val submitting = booksContent(importSheet = importSheet().copy(isConfirming = true))

        val result = reduceImportConfirmationFailure(submitting, "导入服务暂不可用")

        assertFalse(result.importSheet?.isConfirming ?: true)
        assertEquals("导入服务暂不可用", result.importSheet?.nameError)
    }

    /** 构造不依赖协程或仓库的详情内容态。 */
    private fun detailContent() = BookDetailUiState.Content(
        book = testBook(),
        words = emptyList(),
        endReached = true,
    )

    /** 构造只包含导入预览的词书页内容态。 */
    private fun booksContent(importSheet: ImportSheetState) = BooksUiState.Content(
        books = listOf(testBook()),
        currentBookId = null,
        importSheet = importSheet,
    )

    private fun importSheet() = ImportSheetState(
        previewToken = "preview-token",
        suggestedName = "测试导入",
        totalCount = 2,
        previewWords = emptyList(),
    )

    private fun testBook() = BookItem(
        id = 42L,
        name = "测试词书",
        source = BookSource.BUILTIN,
        wordCount = 10,
        selected = false,
        canDelete = false,
    )
}
