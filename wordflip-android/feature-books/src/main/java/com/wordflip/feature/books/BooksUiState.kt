package com.wordflip.feature.books

import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BooksSummary

/** 词书页 UI 状态 */
sealed interface BooksUiState {
    data object Loading : BooksUiState

    data class Content(
        val books: List<BookItem>,
        val groupSize: Int,
        val summary: BooksSummary,
        val isDirty: Boolean,
        val isSaving: Boolean,
    ) : BooksUiState

    data class Error(val message: String) : BooksUiState
}

/** 一次性 UI 事件 */
sealed interface BooksUiEvent {
    data class Toast(val message: String) : BooksUiEvent
    data class ConfirmDelete(val bookId: Long, val bookName: String) : BooksUiEvent
}
