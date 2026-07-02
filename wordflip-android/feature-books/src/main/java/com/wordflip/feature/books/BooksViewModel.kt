package com.wordflip.feature.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.fake.FakeBooksData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 词书页 ViewModel；Mock 阶段本地模拟 PUT /settings 与增量 append（REQ-BOOK-17~21）。
 */
class BooksViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<BooksUiState>(BooksUiState.Loading)
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BooksUiEvent>()
    val events: SharedFlow<BooksUiEvent> = _events.asSharedFlow()

    private var savedBookIds: Set<Long> = emptySet()
    private var savedGroupSize: Int = FakeBooksData.defaultGroupSize()

    init {
        loadBooks()
    }

    fun loadBooks() {
        viewModelScope.launch {
            _uiState.value = BooksUiState.Loading
            delay(200)
            val saved = FakeBooksData.savedSettings()
            savedBookIds = saved.bookIds.toSet()
            savedGroupSize = saved.groupSize
            emitContent(isDirty = false)
        }
    }

    fun toggleBookSelection(bookId: Long) {
        val content = currentContent() ?: return
        val updated = content.books.map { book ->
            if (book.id == bookId) book.copy(selected = !book.selected) else book
        }
        updateContent(updated, content.groupSize)
    }

    fun setGroupSize(size: Int) {
        val content = currentContent() ?: return
        updateContent(content.books, size)
    }

    /** 模拟 PUT /settings；真实环境由服务端 appendGroupsForNewWords 增量追加 */
    fun saveSettings() {
        val content = currentContent() ?: return
        if (content.isSaving) return
        viewModelScope.launch {
            _uiState.value = content.copy(isSaving = true)
            delay(300)
            val selectedIds = content.books.filter { it.selected }.map { it.id }.toSet()
            val result = FakeBooksData.saveSettings(selectedIds, content.groupSize)
            savedBookIds = result.settings.bookIds.toSet()
            savedGroupSize = result.settings.groupSize
            emitContent(isDirty = false, isSaving = false)
            val message = if (result.appendedGroupCount > 0) {
                "设置已保存 · 新增 ${result.appendedGroupCount} 组"
            } else {
                "设置已保存"
            }
            _events.emit(BooksUiEvent.Toast(message))
        }
    }

    fun requestDeleteBook(bookId: Long) {
        val book = currentContent()?.books?.find { it.id == bookId } ?: return
        viewModelScope.launch {
            _events.emit(BooksUiEvent.ConfirmDelete(bookId, book.name))
        }
    }

    /** 删除 imported 词书；已入组词保留（REQ-BOOK-20） */
    fun confirmDeleteBook(bookId: Long) {
        val content = currentContent() ?: return
        if (!FakeBooksData.deleteBook(bookId)) return
        val updated = content.books.filterNot { it.id == bookId }
        updateContent(updated, content.groupSize)
        viewModelScope.launch {
            _events.emit(BooksUiEvent.Toast("词书已删除"))
        }
    }

    fun onImportClick() {
        viewModelScope.launch {
            _events.emit(BooksUiEvent.Toast("导入单词书功能即将上线"))
        }
    }

    fun onCustomGroupClick() {
        viewModelScope.launch {
            _events.emit(BooksUiEvent.Toast("手动添加分组功能即将上线"))
        }
    }

    fun buildSummaryText(summary: com.wordflip.core.model.book.BooksSummary, groupSize: Int): String {
        return "已选 ${summary.distinctSelectedCount} 词（去重后）· 每组 $groupSize 词 · 共约 ${summary.estimatedGroupCount} 组"
    }

    private fun currentContent(): BooksUiState.Content? = _uiState.value as? BooksUiState.Content

    private fun updateContent(books: List<BookItem>, groupSize: Int) {
        val selectedIds = books.filter { it.selected }.map { it.id }.toSet()
        val summary = FakeBooksData.computeSummary(selectedIds, groupSize)
        val isDirty = selectedIds != savedBookIds || groupSize != savedGroupSize
        _uiState.value = BooksUiState.Content(
            books = books,
            groupSize = groupSize,
            summary = summary,
            isDirty = isDirty,
            isSaving = false,
        )
    }

    private fun emitContent(isDirty: Boolean, isSaving: Boolean = false) {
        val books = FakeBooksData.books()
        val selectedIds = books.filter { it.selected }.map { it.id }.toSet()
        val groupSize = savedGroupSize
        _uiState.value = BooksUiState.Content(
            books = books,
            groupSize = groupSize,
            summary = FakeBooksData.computeSummary(selectedIds, groupSize),
            isDirty = isDirty,
            isSaving = isSaving,
        )
    }
}
