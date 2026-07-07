package com.wordflip.feature.books

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.book.BookImportParseException
import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.fake.FakeBooksData
import com.wordflip.core.model.fake.MockBookImportParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 词书页 ViewModel；Mock 阶段本地模拟 PUT /settings、导入与增量 append（REQ-BOOK-5~21）。
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
        updateContent(updated, content.groupSize, importSheet = content.importSheet)
    }

    fun setGroupSize(size: Int) {
        val content = currentContent() ?: return
        updateContent(content.books, size, importSheet = content.importSheet)
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
        updateContent(updated, content.groupSize, importSheet = content.importSheet)
        viewModelScope.launch {
            _events.emit(BooksUiEvent.Toast("词书已删除"))
        }
    }

    /** 触发系统文件选择器（REQ-BOOK-5） */
    fun onImportClick() {
        viewModelScope.launch {
            _events.emit(BooksUiEvent.LaunchFilePicker)
        }
    }

    fun onCustomGroupClick() {
        viewModelScope.launch {
            _events.emit(BooksUiEvent.NavigateToCustomGroup)
        }
    }

    /** 用户选择文件后解析并展示 preview Sheet */
    fun onFileSelected(context: Context, uri: Uri) {
        val content = currentContent() ?: return
        if (content.isParsingImport) return
        viewModelScope.launch {
            _uiState.value = content.copy(isParsingImport = true)
            try {
                val (text, fileName) = withContext(Dispatchers.IO) {
                    readTextFromUri(context, uri)
                }
                val parsed = MockBookImportParser.parse(text, fileName)
                val preview = FakeBooksData.previewImport(parsed)
                val latest = currentContent() ?: return@launch
                _uiState.value = latest.copy(
                    isParsingImport = false,
                    importSheet = ImportSheetState(
                        previewToken = preview.previewToken,
                        suggestedName = preview.suggestedName,
                        totalCount = preview.totalCount,
                        previewWords = preview.previewWords,
                        nameInput = preview.suggestedName,
                    ),
                )
            } catch (e: BookImportParseException) {
                val latest = currentContent() ?: return@launch
                _uiState.value = latest.copy(isParsingImport = false)
                _events.emit(BooksUiEvent.Toast(e.message ?: "未识别到有效单词"))
            } catch (_: Exception) {
                val latest = currentContent() ?: return@launch
                _uiState.value = latest.copy(isParsingImport = false)
                _events.emit(BooksUiEvent.Toast("读取文件失败"))
            }
        }
    }

    fun updateImportName(name: String) {
        val content = currentContent() ?: return
        val sheet = content.importSheet ?: return
        _uiState.value = content.copy(
            importSheet = sheet.copy(nameInput = name, nameError = null),
        )
    }

    fun cancelImport() {
        val content = currentContent() ?: return
        content.importSheet?.previewToken?.let { FakeBooksData.cancelImportPreview(it) }
        _uiState.value = content.copy(importSheet = null)
    }

    /** 确认导入词书（REQ-BOOK-8） */
    fun confirmImport() {
        val content = currentContent() ?: return
        val sheet = content.importSheet ?: return
        if (sheet.isConfirming) return
        val trimmedName = sheet.nameInput.trim()
        if (trimmedName.isEmpty()) {
            _uiState.value = content.copy(
                importSheet = sheet.copy(nameError = "请输入词书名称"),
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = content.copy(importSheet = sheet.copy(isConfirming = true))
            delay(200)
            val result = FakeBooksData.confirmImport(sheet.previewToken, trimmedName)
            if (result == null) {
                val latest = currentContent() ?: return@launch
                _uiState.value = latest.copy(
                    importSheet = sheet.copy(
                        isConfirming = false,
                        nameError = "词书名称已存在或预览已过期",
                    ),
                )
                return@launch
            }
            savedBookIds = FakeBooksData.savedSettings().bookIds.toSet()
            emitContent(isDirty = true, isSaving = false)
            _events.emit(
                BooksUiEvent.Toast("已导入「${result.book.name}」，共 ${result.book.wordCount} 词"),
            )
        }
    }

    fun buildSummaryText(summary: com.wordflip.core.model.book.BooksSummary, groupSize: Int): String {
        return "已选 ${summary.distinctSelectedCount} 词（去重后）· 每组 $groupSize 词 · 共约 ${summary.estimatedGroupCount} 组"
    }

    private fun currentContent(): BooksUiState.Content? = _uiState.value as? BooksUiState.Content

    private fun updateContent(
        books: List<BookItem>,
        groupSize: Int,
        importSheet: ImportSheetState?,
        isParsingImport: Boolean = false,
    ) {
        val selectedIds = books.filter { it.selected }.map { it.id }.toSet()
        val summary = FakeBooksData.computeSummary(selectedIds, groupSize)
        val isDirty = selectedIds != savedBookIds || groupSize != savedGroupSize
        _uiState.value = BooksUiState.Content(
            books = books,
            groupSize = groupSize,
            summary = summary,
            isDirty = isDirty,
            isSaving = false,
            importSheet = importSheet,
            isParsingImport = isParsingImport,
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
            importSheet = null,
            isParsingImport = false,
        )
    }

    private fun readTextFromUri(context: Context, uri: Uri): Pair<String, String> {
        val fileName = uri.lastPathSegment ?: "import.txt"
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw BookImportParseException("未识别到有效单词")
        return text to fileName
    }
}
