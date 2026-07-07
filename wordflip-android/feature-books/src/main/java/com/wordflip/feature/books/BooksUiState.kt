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
        val importSheet: ImportSheetState? = null,
        val isParsingImport: Boolean = false,
    ) : BooksUiState

    data class Error(val message: String) : BooksUiState
}

/** 导入预览 Sheet 状态（REQ-BOOK-5~9） */
data class ImportSheetState(
    val previewToken: String,
    val suggestedName: String,
    val totalCount: Int,
    val previewWords: List<com.wordflip.core.model.study.WordSummary>,
    val nameInput: String = suggestedName,
    val nameError: String? = null,
    val isConfirming: Boolean = false,
)

/** 一次性 UI 事件 */
sealed interface BooksUiEvent {
    data class Toast(val message: String) : BooksUiEvent
    data class ConfirmDelete(val bookId: Long, val bookName: String) : BooksUiEvent
    data object NavigateToCustomGroup : BooksUiEvent
    data object LaunchFilePicker : BooksUiEvent
}
