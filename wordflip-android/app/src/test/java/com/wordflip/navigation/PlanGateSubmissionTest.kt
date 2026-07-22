package com.wordflip.navigation

import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanGateSubmissionTest {
    @Test
    fun `首次提交时保留词书并同步占用提交态`() {
        val books = listOf(testBook())
        val state = PlanGateState.SelectBook(books = books)

        val result = preparePlanBookSubmission(state, bookId = 42L)

        assertEquals(PlanGateState.SelectBook(books = books, submittingBookId = 42L), result)
    }

    @Test
    fun `已有提交时拒绝再次提交`() {
        val state = PlanGateState.SelectBook(books = listOf(testBook()), submittingBookId = 7L)

        assertNull(preparePlanBookSubmission(state, bookId = 42L))
    }

    @Test
    fun `非词书选择态时拒绝提交`() {
        assertNull(preparePlanBookSubmission(PlanGateState.Loading, bookId = 42L))
    }

    private fun testBook() = BookItem(
        id = 1L,
        name = "测试词书",
        source = BookSource.BUILTIN,
        wordCount = 10,
        selected = false,
        canDelete = false,
    )
}
