package com.wordflip.feature.books

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.book.BookImportParseException
import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BooksSummary
import com.wordflip.core.model.book.GroupStrategy
import com.wordflip.core.model.fake.FakeBooksData
import com.wordflip.core.model.fake.MockBookImportParser
import com.wordflip.core.network.books.BooksSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * 词书页 ViewModel：Hub 概览 + 增加书籍/重新分组向导（REQ-BOOK-17~26）。
 */
@HiltViewModel
class BooksViewModel @Inject constructor(
    private val booksSettingsRepository: BooksSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BooksUiState>(BooksUiState.Loading)
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BooksUiEvent>()
    val events: SharedFlow<BooksUiEvent> = _events.asSharedFlow()

    private var savedBookIds: Set<Long> = emptySet()
    private var savedGroupSize: Int = 20
    private var savedGroupStrategy: GroupStrategy = GroupStrategy.BOOK_ORDER
    private var serverSummary: BooksSummary = BooksSummary(0, 0, 0)

    /** 已有内容时静默刷新，避免 Tab 切换闪 Loading */
    fun loadBooks() {
        viewModelScope.launch {
            val current = _uiState.value as? BooksUiState.Content
            val inWizard = current?.wizardMode != null
            val showLoading = current == null
            if (showLoading) {
                _uiState.value = BooksUiState.Loading
            }
            booksSettingsRepository.loadBooksPage()
                .onSuccess { page ->
                    savedBookIds = page.settings.bookIds.toSet()
                    savedGroupSize = page.settings.groupSize
                    savedGroupStrategy = page.settings.groupStrategy
                    serverSummary = page.settings.summary
                    _uiState.value = BooksUiState.Content(
                        books = page.books.map { book ->
                            book.copy(selected = book.id in savedBookIds)
                        },
                        groupSize = page.settings.groupSize,
                        groupStrategy = page.settings.groupStrategy,
                        summary = page.settings.summary,
                        isDirty = false,
                        isSaving = false,
                        importSheet = if (inWizard) current?.importSheet else null,
                        isParsingImport = false,
                        wizardMode = if (inWizard) current?.wizardMode else null,
                        wizardStep = if (inWizard) current?.wizardStep else null,
                        lockedBookIds = if (inWizard) current?.lockedBookIds.orEmpty() else emptySet(),
                    )
                }
                .onFailure { error ->
                    _uiState.value = BooksUiState.Error(
                        message = error.message ?: "加载词书失败",
                    )
                }
        }
    }

    /** 启动向导：增加书籍（锁定已选）或重新分组（全可选） */
    fun startWizard(mode: BooksWizardMode) {
        val content = currentContent() ?: return
        val books = content.books.map { book ->
            book.copy(selected = book.id in savedBookIds)
        }
        publishContent(
            books = books,
            groupSize = savedGroupSize,
            groupStrategy = savedGroupStrategy,
            importSheet = content.importSheet,
            isParsingImport = content.isParsingImport,
            isSaving = false,
            wizardMode = mode,
            wizardStep = BooksWizardStep.SELECT_BOOKS,
            lockedBookIds = if (mode == BooksWizardMode.ADD) savedBookIds else emptySet(),
        )
    }

    fun cancelWizard() {
        val content = currentContent() ?: return
        publishContent(
            books = content.books.map { book -> book.copy(selected = book.id in savedBookIds) },
            groupSize = savedGroupSize,
            groupStrategy = savedGroupStrategy,
            importSheet = content.importSheet,
            isParsingImport = content.isParsingImport,
            isSaving = false,
            wizardMode = null,
            wizardStep = null,
            lockedBookIds = emptySet(),
        )
    }

    fun wizardBack() {
        val content = currentContent() ?: return
        val step = content.wizardStep ?: return
        val previous = when (step) {
            BooksWizardStep.SELECT_BOOKS -> {
                cancelWizard()
                return
            }
            BooksWizardStep.STRATEGY -> BooksWizardStep.SELECT_BOOKS
            BooksWizardStep.CONFIRM -> BooksWizardStep.STRATEGY
        }
        _uiState.value = content.copy(wizardStep = previous)
    }

    fun wizardNext() {
        val content = currentContent() ?: return
        val mode = content.wizardMode ?: return
        when (content.wizardStep) {
            BooksWizardStep.SELECT_BOOKS -> {
                val selectedIds = content.books.filter { it.selected }.map { it.id }.toSet()
                if (mode == BooksWizardMode.ADD) {
                    val newIds = selectedIds - savedBookIds
                    if (newIds.isEmpty()) {
                        emitToast("请至少勾选一本新词书")
                        return
                    }
                } else if (selectedIds.isEmpty()) {
                    emitToast("请至少勾选一本词书")
                    return
                }
                _uiState.value = content.copy(wizardStep = BooksWizardStep.STRATEGY)
            }
            BooksWizardStep.STRATEGY -> {
                _uiState.value = content.copy(wizardStep = BooksWizardStep.CONFIRM)
            }
            BooksWizardStep.CONFIRM -> Unit
            null -> Unit
        }
    }

    /** 向导最后一步保存 */
    fun wizardSave() {
        val content = currentContent() ?: return
        val mode = content.wizardMode ?: return
        saveSettings(regroup = mode == BooksWizardMode.REGROUP)
    }

    fun toggleBookSelection(bookId: Long) {
        val content = currentContent() ?: return
        if (content.wizardMode == BooksWizardMode.ADD && bookId in content.lockedBookIds) {
            return
        }
        val updated = content.books.map { book ->
            if (book.id == bookId) book.copy(selected = !book.selected) else book
        }
        publishContent(
            books = updated,
            groupSize = content.groupSize,
            groupStrategy = content.groupStrategy,
            importSheet = content.importSheet,
            isParsingImport = content.isParsingImport,
            isSaving = content.isSaving,
            wizardMode = content.wizardMode,
            wizardStep = content.wizardStep,
            lockedBookIds = content.lockedBookIds,
        )
    }

    fun setGroupSize(size: Int) {
        val content = currentContent() ?: return
        publishContent(
            books = content.books,
            groupSize = size,
            groupStrategy = content.groupStrategy,
            importSheet = content.importSheet,
            isParsingImport = content.isParsingImport,
            isSaving = content.isSaving,
            wizardMode = content.wizardMode,
            wizardStep = content.wizardStep,
            lockedBookIds = content.lockedBookIds,
        )
    }

    /** 切换自动分组策略（REQ-BOOK-22） */
    fun setGroupStrategy(strategy: GroupStrategy) {
        val content = currentContent() ?: return
        publishContent(
            books = content.books,
            groupSize = content.groupSize,
            groupStrategy = strategy,
            importSheet = content.importSheet,
            isParsingImport = content.isParsingImport,
            isSaving = content.isSaving,
            wizardMode = content.wizardMode,
            wizardStep = content.wizardStep,
            lockedBookIds = content.lockedBookIds,
        )
    }

    /** PUT /settings；regroup=false 增量追加，true 重建 auto 组（REQ-BOOK-26） */
    fun saveSettings(regroup: Boolean = false) {
        val content = currentContent() ?: return
        if (content.isSaving) return
        val selectedIds = content.books.filter { it.selected }.map { it.id }
        if (regroup && selectedIds.isEmpty()) {
            emitToast("请至少勾选一本词书")
            return
        }
        if (!regroup && content.wizardMode == null && !content.isDirty) return
        viewModelScope.launch {
            _uiState.value = content.copy(isSaving = true)
            booksSettingsRepository.saveBooksSettings(
                selectedIds,
                content.groupSize,
                content.groupStrategy,
                regroup = regroup,
            )
                .onSuccess { response ->
                    savedBookIds = response.bookIds.toSet()
                    savedGroupSize = response.groupSize
                    savedGroupStrategy = response.groupStrategy
                    serverSummary = response.summary
                    val updatedBooks = content.books.map { book ->
                        book.copy(selected = book.id in savedBookIds)
                    }
                    _uiState.value = BooksUiState.Content(
                        books = updatedBooks,
                        groupSize = response.groupSize,
                        groupStrategy = response.groupStrategy,
                        summary = response.summary,
                        isDirty = false,
                        isSaving = false,
                        importSheet = null,
                        isParsingImport = false,
                        wizardMode = null,
                        wizardStep = null,
                        lockedBookIds = emptySet(),
                    )
                    val message = when {
                        regroup && response.appendedGroups.count > 0 ->
                            "已重新分组 · 共 ${response.appendedGroups.count} 组"
                        regroup -> "已重新分组"
                        response.appendedGroups.count > 0 ->
                            "设置已保存 · 新增 ${response.appendedGroups.count} 组"
                        else -> "设置已保存"
                    }
                    _events.emit(BooksUiEvent.Toast(message))
                }
                .onFailure { error ->
                    _uiState.value = content.copy(isSaving = false)
                    _events.emit(BooksUiEvent.Toast(error.message ?: "保存失败"))
                }
        }
    }

    fun requestDeleteBook(bookId: Long) {
        val book = currentContent()?.books?.find { it.id == bookId } ?: return
        viewModelScope.launch {
            _events.emit(BooksUiEvent.ConfirmDelete(bookId, book.name))
        }
    }

    /** 删除 imported 词书；仍 Mock，待 DELETE API（P0-B24） */
    fun confirmDeleteBook(bookId: Long) {
        val content = currentContent() ?: return
        if (!FakeBooksData.deleteBook(bookId)) return
        val updated = content.books.filterNot { it.id == bookId }
        publishContent(
            books = updated,
            groupSize = content.groupSize,
            groupStrategy = content.groupStrategy,
            importSheet = content.importSheet,
            isParsingImport = content.isParsingImport,
            isSaving = content.isSaving,
            wizardMode = content.wizardMode,
            wizardStep = content.wizardStep,
            lockedBookIds = content.lockedBookIds,
        )
        emitToast("词书已删除")
    }

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

    /** 确认导入词书；仍 Mock（P0-B23） */
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
            loadBooks()
            _events.emit(
                BooksUiEvent.Toast("已导入「${result.book.name}」，共 ${result.book.wordCount} 词"),
            )
        }
    }

    fun buildSummaryText(summary: BooksSummary, groupSize: Int): String {
        return "已选 ${summary.distinctSelectedCount} 词（去重后）· 每组 $groupSize 词 · 共约 ${summary.estimatedGroupCount} 组"
    }

    fun wizardStepLabel(step: BooksWizardStep?): String = when (step) {
        BooksWizardStep.SELECT_BOOKS -> "1/3 选择词书"
        BooksWizardStep.STRATEGY -> "2/3 分组策略"
        BooksWizardStep.CONFIRM -> "3/3 确认保存"
        null -> ""
    }

    fun wizardTitle(mode: BooksWizardMode?): String = when (mode) {
        BooksWizardMode.ADD -> "增加书籍"
        BooksWizardMode.REGROUP -> "重新分组"
        null -> "词书"
    }

    private fun currentContent(): BooksUiState.Content? = _uiState.value as? BooksUiState.Content

    private fun publishContent(
        books: List<BookItem>,
        groupSize: Int,
        groupStrategy: GroupStrategy,
        importSheet: ImportSheetState?,
        isParsingImport: Boolean,
        isSaving: Boolean,
        wizardMode: BooksWizardMode?,
        wizardStep: BooksWizardStep?,
        lockedBookIds: Set<Long>,
    ) {
        val selectedIds = books.filter { it.selected }.map { it.id }.toSet()
        val summary = estimateSummary(books, selectedIds, groupSize)
        val isDirty = selectedIds != savedBookIds
            || groupSize != savedGroupSize
            || groupStrategy != savedGroupStrategy
        _uiState.value = BooksUiState.Content(
            books = books,
            groupSize = groupSize,
            groupStrategy = groupStrategy,
            summary = summary,
            isDirty = isDirty,
            isSaving = isSaving,
            importSheet = importSheet,
            isParsingImport = isParsingImport,
            wizardMode = wizardMode,
            wizardStep = wizardStep,
            lockedBookIds = lockedBookIds,
        )
    }

    /** 编辑态本地粗算汇总；保存后以服务端 summary 为准 */
    private fun estimateSummary(
        books: List<BookItem>,
        selectedIds: Set<Long>,
        groupSize: Int,
    ): BooksSummary {
        if (selectedIds == savedBookIds && groupSize == savedGroupSize) {
            return serverSummary
        }
        val distinctCount = books.filter { it.id in selectedIds }.sumOf { it.wordCount }
        val estimatedGroups = if (groupSize > 0 && distinctCount > 0) {
            ceil(distinctCount.toDouble() / groupSize).toInt()
        } else {
            0
        }
        return BooksSummary(
            distinctSelectedCount = distinctCount,
            estimatedGroupCount = estimatedGroups,
            unassignedCount = serverSummary.unassignedCount,
        )
    }

    private fun emitToast(message: String) {
        viewModelScope.launch {
            _events.emit(BooksUiEvent.Toast(message))
        }
    }

    private fun readTextFromUri(context: Context, uri: Uri): Pair<String, String> {
        val fileName = uri.lastPathSegment ?: "import.txt"
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw BookImportParseException("未识别到有效单词")
        return text to fileName
    }
}
