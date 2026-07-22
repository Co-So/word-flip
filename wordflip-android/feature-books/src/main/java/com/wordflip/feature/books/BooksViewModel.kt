package com.wordflip.feature.books

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** 词书页：开始或切换唯一当前学习计划，并保留历史计划。 */
@HiltViewModel
class BooksViewModel @Inject constructor(
    private val repository: BooksSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<BooksUiState>(BooksUiState.Loading)
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<BooksUiEvent>()
    val events: SharedFlow<BooksUiEvent> = _events.asSharedFlow()

    fun loadBooks() {
        viewModelScope.launch {
            repository.loadBooksPage().fold(
                onSuccess = { page ->
                    val currentId = page.currentPlan?.bookId
                    _uiState.value = BooksUiState.Content(
                        books = page.books.map { it.copy(selected = it.id == currentId) },
                        currentBookId = currentId,
                    )
                },
                onFailure = { _uiState.value = BooksUiState.Error(it.message ?: "加载词书失败") },
            )
        }
    }

    fun startBook(bookId: Long) {
        val content = _uiState.value as? BooksUiState.Content ?: return
        if (content.switchingBookId != null || content.currentBookId == bookId) return
        viewModelScope.launch {
            _uiState.value = content.copy(switchingBookId = bookId)
            repository.startBook(bookId).fold(
                onSuccess = {
                    _events.emit(BooksUiEvent.Toast(if (content.currentBookId == null) "学习计划已创建" else "主词书已切换"))
                    loadBooks()
                },
                onFailure = {
                    _uiState.value = content
                    _events.emit(BooksUiEvent.Toast(it.message ?: "切换失败"))
                },
            )
        }
    }

    fun openBookDetail(bookId: Long) = emit(BooksUiEvent.NavigateToBookDetail(bookId))
    fun onCustomGroupClick() = emit(BooksUiEvent.NavigateToCustomGroup)
    fun onImportClick() = emit(BooksUiEvent.LaunchFilePicker)

    fun requestDeleteBook(bookId: Long) {
        val book = (_uiState.value as? BooksUiState.Content)?.books?.find { it.id == bookId } ?: return
        emit(BooksUiEvent.ConfirmDelete(book.id, book.name))
    }

    fun confirmDeleteBook(bookId: Long) {
        viewModelScope.launch {
            repository.deleteBook(bookId).fold(
                onSuccess = { loadBooks() },
                onFailure = { _events.emit(BooksUiEvent.Toast(it.message ?: "删除失败")) },
            )
        }
    }

    fun onFileSelected(context: Context, uri: Uri) {
        val content = _uiState.value as? BooksUiState.Content ?: return
        viewModelScope.launch {
            _uiState.value = content.copy(isParsingImport = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取文件")
                    Triple(bytes, uri.lastPathSegment?.substringAfterLast('/') ?: "import.txt", context.contentResolver.getType(uri) ?: "text/plain")
                }
            }.fold(
                onSuccess = { (bytes, name, mime) ->
                    repository.previewImport(bytes, name, mime).fold(
                        onSuccess = { preview ->
                            _uiState.value = content.copy(
                                importSheet = ImportSheetState(preview.previewToken, preview.suggestedName, preview.totalCount, preview.previewWords),
                            )
                        },
                        onFailure = { fail(content, it.message ?: "导入预览失败") },
                    )
                },
                onFailure = { fail(content, "读取文件失败") },
            )
        }
    }

    fun updateImportName(name: String) {
        val content = _uiState.value as? BooksUiState.Content ?: return
        val sheet = content.importSheet ?: return
        if (sheet.isConfirming) return
        _uiState.value = content.copy(importSheet = sheet.copy(nameInput = name, nameError = null))
    }

    fun cancelImport() {
        val content = _uiState.value as? BooksUiState.Content ?: return
        // 确认请求期间保持预览状态，禁止通过过期回调取消 Sheet。
        if (content.importSheet?.isConfirming == true) return
        _uiState.value = content.copy(importSheet = null)
    }

    fun confirmImport() {
        val content = _uiState.value as? BooksUiState.Content ?: return
        val sheet = content.importSheet ?: return
        val name = sheet.nameInput.trim()
        if (name.isEmpty()) {
            _uiState.value = content.copy(importSheet = sheet.copy(nameError = "请输入词书名称"))
            return
        }
        val confirming = prepareImportConfirmation(content) ?: return
        // 在协程启动前同步占用确认态，避免重复导入同一预览令牌。
        _uiState.value = confirming
        viewModelScope.launch {
            repository.confirmImport(sheet.previewToken, name).fold(
                onSuccess = {
                    _events.emit(BooksUiEvent.Toast("词书已导入；待补充卡不会进入测验池"))
                    loadBooks()
                },
                onFailure = {
                    val message = it.message ?: "导入失败"
                    val current = _uiState.value as? BooksUiState.Content ?: confirming
                    _uiState.value = reduceImportConfirmationFailure(current, message)
                    _events.emit(BooksUiEvent.Toast(message))
                },
            )
        }
    }

    /** 旧详情页回传直接等价为切换计划。 */
    fun prepareJoinLearning(bookId: Long) = startBook(bookId)

    private fun emit(event: BooksUiEvent) {
        viewModelScope.launch { _events.emit(event) }
    }

    private fun fail(content: BooksUiState.Content, message: String) {
        _uiState.value = content.copy(isParsingImport = false)
        emit(BooksUiEvent.Toast(message))
    }
}

/** 为导入确认同步申请提交资格；没有预览或已有确认请求时拒绝。 */
internal fun prepareImportConfirmation(
    state: BooksUiState.Content,
): BooksUiState.Content? {
    val sheet = state.importSheet ?: return null
    if (sheet.isConfirming) return null
    return state.copy(importSheet = sheet.copy(isConfirming = true, nameError = null))
}

/** 导入确认失败后恢复编辑，并在 Sheet 内保留服务端错误。 */
internal fun reduceImportConfirmationFailure(
    state: BooksUiState.Content,
    message: String,
): BooksUiState.Content = state.copy(
    importSheet = state.importSheet?.copy(
        isConfirming = false,
        nameError = message,
    ),
)
