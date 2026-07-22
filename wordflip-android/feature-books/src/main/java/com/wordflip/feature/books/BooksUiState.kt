package com.wordflip.feature.books

import com.wordflip.core.model.book.BookItem

/** 词书页只维护一本当前主词书。 */
sealed interface BooksUiState {
    data object Loading : BooksUiState
    data class Content(
        val books: List<BookItem>,
        val currentBookId: Long?,
        val switchingBookId: Long? = null,
        val importSheet: ImportSheetState? = null,
        val isParsingImport: Boolean = false,
    ) : BooksUiState
    data class Error(val message: String) : BooksUiState
}

data class ImportSheetState(
    val previewToken: String,
    val suggestedName: String,
    val totalCount: Int,
    val previewWords: List<com.wordflip.core.model.study.WordSummary>,
    val nameInput: String = suggestedName,
    val nameError: String? = null,
    val isConfirming: Boolean = false,
)

sealed interface BooksUiEvent {
    data class Toast(val message: String) : BooksUiEvent
    data class ConfirmDelete(val bookId: Long, val bookName: String) : BooksUiEvent
    data object NavigateToCustomGroup : BooksUiEvent
    data object LaunchFilePicker : BooksUiEvent
    data class NavigateToBookDetail(val bookId: Long) : BooksUiEvent
}
