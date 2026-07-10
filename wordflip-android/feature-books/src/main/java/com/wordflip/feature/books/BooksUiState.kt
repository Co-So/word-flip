package com.wordflip.feature.books

import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BooksSummary
import com.wordflip.core.model.book.GroupStrategy

/** 词书向导模式：增量追加 vs 全量重建 auto 组 */
enum class BooksWizardMode {
    ADD,
    REGROUP,
}

/** 向导步骤：勾选词书 → 策略 → 确认保存 */
enum class BooksWizardStep {
    SELECT_BOOKS,
    STRATEGY,
    CONFIRM,
}

/** 词书页 UI 状态 */
sealed interface BooksUiState {
    data object Loading : BooksUiState

    data class Content(
        val books: List<BookItem>,
        val groupSize: Int,
        val groupStrategy: GroupStrategy,
        val summary: BooksSummary,
        val isDirty: Boolean,
        val isSaving: Boolean,
        val importSheet: ImportSheetState? = null,
        val isParsingImport: Boolean = false,
        /** 非 null 时处于向导流程 */
        val wizardMode: BooksWizardMode? = null,
        val wizardStep: BooksWizardStep? = null,
        /** ADD 模式下已保存词书 id，Checkbox 锁定不可取消 */
        val lockedBookIds: Set<Long> = emptySet(),
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
    data class NavigateToBookDetail(val bookId: Long) : BooksUiEvent
}
