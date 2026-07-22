package com.wordflip.feature.books

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BookSource
import com.wordflip.core.model.study.WordSummary
import com.wordflip.core.network.books.BooksSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 词书详情：只读词条分页；imported 可删；未在学可「加入学习」。
 */
@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val booksSettingsRepository: BooksSettingsRepository,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle["bookId"])

    private val _uiState = MutableStateFlow<BookDetailUiState>(BookDetailUiState.Loading)
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BookDetailEvent>()
    val events: SharedFlow<BookDetailEvent> = _events.asSharedFlow()

    private var nextPage = 1
    private var totalPages = 1
    private var loadingMore = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = BookDetailUiState.Loading
            nextPage = 1
            booksSettingsRepository.getBook(bookId)
                .onSuccess { book ->
                    loadFirstPage(book)
                }
                .onFailure { error ->
                    _uiState.value = BookDetailUiState.Error(error.message ?: "加载失败")
                }
        }
    }

    fun loadMore() {
        val content = _uiState.value as? BookDetailUiState.Content ?: return
        if (loadingMore || nextPage >= totalPages) return
        viewModelScope.launch {
            loadingMore = true
            booksSettingsRepository.listBookCards(bookId, nextPage)
                .onSuccess { page ->
                    totalPages = page.totalPages.coerceAtLeast(1)
                    nextPage = page.page + 1
                    _uiState.value = content.copy(
                        words = content.words + page.cards,
                        endReached = nextPage >= totalPages,
                    )
                }
            loadingMore = false
        }
    }

    fun requestDelete() {
        val content = _uiState.value as? BookDetailUiState.Content ?: return
        if (!content.book.canDelete) return
        viewModelScope.launch {
            _events.emit(BookDetailEvent.ConfirmDelete(content.book.id, content.book.name))
        }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            booksSettingsRepository.deleteBook(bookId)
                .onSuccess {
                    _events.emit(BookDetailEvent.Deleted)
                }
                .onFailure { error ->
                    _events.emit(BookDetailEvent.Toast(error.message ?: "删除失败"))
                }
        }
    }

    fun joinLearning() {
        val content = prepareBookDetailJoinSubmission(_uiState.value) ?: return
        // 在协程启动前同步占用提交态，避免快速点击发出重复请求。
        _uiState.value = content
        viewModelScope.launch {
            booksSettingsRepository.startBook(content.book.id)
                .onSuccess { _events.emit(BookDetailEvent.JoinLearning(content.book.id)) }
                .onFailure { error ->
                    val current = _uiState.value as? BookDetailUiState.Content ?: content
                    _uiState.value = current.copy(isJoiningLearning = false)
                    _events.emit(BookDetailEvent.Toast(error.message ?: "切换学习计划失败"))
                }
        }
    }

    private suspend fun loadFirstPage(book: BookItem) {
        booksSettingsRepository.listBookCards(bookId, 1)
            .onSuccess { page ->
                totalPages = page.totalPages.coerceAtLeast(1)
                nextPage = page.page + 1
                _uiState.value = BookDetailUiState.Content(
                    book = book,
                    words = page.cards,
                    endReached = nextPage >= totalPages,
                )
            }
            .onFailure { error ->
                _uiState.value = BookDetailUiState.Error(error.message ?: "加载词条失败")
            }
    }
}

sealed interface BookDetailUiState {
    data object Loading : BookDetailUiState
    data class Content(
        val book: BookItem,
        val words: List<WordSummary>,
        val endReached: Boolean,
        val isJoiningLearning: Boolean = false,
    ) : BookDetailUiState
    data class Error(val message: String) : BookDetailUiState
}

sealed interface BookDetailEvent {
    data class Toast(val message: String) : BookDetailEvent
    data class ConfirmDelete(val bookId: Long, val bookName: String) : BookDetailEvent
    data object Deleted : BookDetailEvent
    data class JoinLearning(val bookId: Long) : BookDetailEvent
}

/** 为详情页加入学习同步申请提交资格；已有提交或状态不匹配时拒绝。 */
internal fun prepareBookDetailJoinSubmission(
    state: BookDetailUiState,
): BookDetailUiState.Content? {
    val content = state as? BookDetailUiState.Content ?: return null
    if (content.isJoiningLearning) return null
    return content.copy(isJoiningLearning = true)
}

fun BookItem.sourceLabel(): String = when (source) {
    BookSource.BUILTIN -> "系统词书"
    BookSource.IMPORTED -> "我的导入"
}
