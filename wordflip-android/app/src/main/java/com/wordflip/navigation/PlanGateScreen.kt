package com.wordflip.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.BookSource
import com.wordflip.core.network.books.BooksSettingsRepository
import com.wordflip.core.network.learning.LearningRepository
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.AppleInfoCard
import com.wordflip.core.ui.apple.ApplePageTitle
import com.wordflip.core.ui.apple.ApplePrimaryAction
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.apple.applePress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 登录后先检查唯一当前计划；没有计划时必须先选一本词书。 */
@HiltViewModel
class PlanGateViewModel @Inject constructor(
    private val learningRepository: LearningRepository,
    private val booksRepository: BooksSettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<PlanGateState>(PlanGateState.Loading)
    val state: StateFlow<PlanGateState> = _state.asStateFlow()

    fun check() {
        viewModelScope.launch {
            _state.value = PlanGateState.Loading
            learningRepository.currentPlan().fold(
                onSuccess = { plan ->
                    if (plan != null) {
                        _state.value = PlanGateState.Ready
                    } else {
                        booksRepository.loadBooksPage().fold(
                            onSuccess = { _state.value = PlanGateState.SelectBook(it.books) },
                            onFailure = { _state.value = PlanGateState.Error(it.message ?: "加载词书失败") },
                        )
                    }
                },
                onFailure = { _state.value = PlanGateState.Error(it.message ?: "检查学习计划失败") },
            )
        }
    }

    fun startBook(bookId: Long) {
        val next = preparePlanBookSubmission(_state.value, bookId) ?: return
        // 在协程启动前同步占用提交态，避免连续点击读取到同一旧状态。
        _state.value = next
        viewModelScope.launch {
            learningRepository.startBook(bookId).fold(
                onSuccess = { _state.value = PlanGateState.Ready },
                onFailure = { _state.value = PlanGateState.Error(it.message ?: "创建学习计划失败") },
            )
        }
    }
}

sealed interface PlanGateState {
    data object Loading : PlanGateState
    data object Ready : PlanGateState
    data class SelectBook(val books: List<BookItem>, val submittingBookId: Long? = null) : PlanGateState
    data class Error(val message: String) : PlanGateState
}

/** 为首次计划创建同步申请提交资格；已有进行中的提交或状态不匹配时拒绝。 */
internal fun preparePlanBookSubmission(
    state: PlanGateState,
    bookId: Long,
): PlanGateState.SelectBook? {
    val selectBook = state as? PlanGateState.SelectBook ?: return null
    if (selectBook.submittingBookId != null) return null
    return selectBook.copy(submittingBookId = bookId)
}

@Composable
fun PlanGateScreen(
    onReady: () -> Unit,
    viewModel: PlanGateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.check() }
    LaunchedEffect(state) { if (state == PlanGateState.Ready) onReady() }
    when (val value = state) {
        PlanGateState.Loading, PlanGateState.Ready -> PlanGateLoading()
        is PlanGateState.Error -> PlanGateError(
            message = value.message,
            onRetry = viewModel::check,
        )
        is PlanGateState.SelectBook -> PlanGateBookSelection(
            books = value.books,
            submittingBookId = value.submittingBookId,
            onBookClick = viewModel::startBook,
        )
    }
}

/** 计划检查与成功跳转期间保留稳定的整页加载反馈。 */
@Composable
private fun PlanGateLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleUi.colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = AppleUi.colors.accent,
                strokeWidth = 2.5.dp,
            )
            Text(
                text = "正在准备你的学习空间…",
                style = MaterialTheme.typography.bodyMedium,
                color = AppleUi.colors.secondaryText,
            )
        }
    }
}

/** 计划门错误态：保留原错误信息与就地重试入口。 */
@Composable
private fun PlanGateError(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleUi.colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppleGroupedSurface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = AppleUi.colors.destructive,
                )
                Text(
                    text = "暂时无法建立学习计划",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppleUi.colors.primaryText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppleUi.colors.secondaryText,
                )
                Spacer(modifier = Modifier.size(4.dp))
                ApplePrimaryAction(
                    text = "重试",
                    onClick = onRetry,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                        )
                    },
                )
            }
        }
    }
}

/** 首次词书选择页，只消费服务端返回的词书字段，不在客户端推导计划。 */
@Composable
private fun PlanGateBookSelection(
    books: List<BookItem>,
    submittingBookId: Long?,
    onBookClick: (Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleUi.colors.canvas),
    ) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = AppleUi.colors.accent.copy(alpha = 0.12f),
                        tonalElevation = 0.dp,
                    ) {
                        Box(
                            modifier = Modifier.size(52.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoStories,
                                contentDescription = null,
                                tint = AppleUi.colors.accent,
                            )
                        }
                    }
                    ApplePageTitle(
                        title = "建立学习计划",
                        subtitle = "选择一本主词书开始。之后可随时切换，历史进度会保留。",
                    )
                    Text(
                        text = "选择主词书",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppleUi.colors.primaryText,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (books.isEmpty()) {
                item {
                    AppleGroupedSurface(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "暂时没有可选词书，请稍后重试。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppleUi.colors.secondaryText,
                        )
                    }
                }
            } else {
                items(books, key = { it.id }) { book ->
                    PlanGateBookCard(
                        book = book,
                        submitting = submittingBookId == book.id,
                        selectionEnabled = submittingBookId == null,
                        onClick = { onBookClick(book.id) },
                    )
                }
            }
        }
    }
}

/** 词书卡在提交期间仅为当前项展示进度，同时锁住列表避免重复提交。 */
@Composable
private fun PlanGateBookCard(
    book: BookItem,
    submitting: Boolean,
    selectionEnabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember(book.id) { MutableInteractionSource() }
    AppleInfoCard(
        title = book.name,
        supportingText = "${book.source.displayName()} · ${book.wordCount} 张卡",
        modifier = Modifier
            .fillMaxWidth()
            .applePress(interactionSource, enabled = selectionEnabled)
            .clickable(
                enabled = selectionEnabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = AppleUi.colors.accent.copy(alpha = 0.12f),
                tonalElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = AppleUi.colors.accent,
                    )
                }
            }
        },
        trailingContent = {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = AppleUi.colors.accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = AppleUi.colors.secondaryText,
                )
            }
        },
    )
}

/** 将现有词书来源枚举转换为面向用户的简短说明。 */
private fun BookSource.displayName(): String = when (this) {
    BookSource.BUILTIN -> "内置词书"
    BookSource.IMPORTED -> "我的导入"
}
