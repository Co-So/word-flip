package com.wordflip.feature.books

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.model.book.BookItem
import com.wordflip.core.ui.apple.AppleContextActionRow
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.ApplePrimaryAction
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.apple.applePress
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.rememberWordFlipToast

/** 词书页：突出唯一当前学习计划，并保留词书切换、导入与分组入口。 */
@Composable
fun BooksScreen(
    onNavigateToCustomGroup: () -> Unit = {},
    onNavigateToBookDetail: (Long) -> Unit = {},
    joinLearningBookId: Long? = null,
    onJoinLearningConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BooksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val (host, toast) = rememberWordFlipToast()
    var pendingDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.onFileSelected(context, it) }
    }

    LaunchedEffect(Unit) { viewModel.loadBooks() }
    LaunchedEffect(joinLearningBookId) {
        handleBookDetailJoinReturn(
            bookId = joinLearningBookId,
            onConsumed = onJoinLearningConsumed,
            onRefresh = viewModel::loadBooks,
        )
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BooksUiEvent.Toast -> toast.show(event.message)
                is BooksUiEvent.ConfirmDelete -> pendingDelete = event.bookId to event.bookName
                BooksUiEvent.NavigateToCustomGroup -> onNavigateToCustomGroup()
                BooksUiEvent.LaunchFilePicker -> picker.launch(
                    arrayOf("text/*", "application/json", "text/csv", "application/csv"),
                )
                is BooksUiEvent.NavigateToBookDetail -> onNavigateToBookDetail(event.bookId)
            }
        }
    }

    pendingDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除或归档词书") },
            text = { Text("确定处理「$name」？历史计划存在时该词书只会归档，已有学习记录不会丢失。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmDeleteBook(id)
                        pendingDelete = null
                    },
                ) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppleUi.colors.canvas,
        topBar = { WordFlipTopBar(title = "词书") },
        snackbarHost = { WordFlipToastHost(host) },
    ) { padding ->
        when (val value = state) {
            BooksUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AppleUi.colors.accent)
                }
            }
            is BooksUiState.Error -> NetworkErrorView(
                message = value.message,
                onRetry = viewModel::loadBooks,
                modifier = Modifier.padding(padding),
            )
            is BooksUiState.Content -> BooksContent(
                state = value,
                onImport = viewModel::onImportClick,
                onCustomGroup = viewModel::onCustomGroupClick,
                onOpenDetail = viewModel::openBookDetail,
                onStartBook = viewModel::startBook,
                onDeleteBook = viewModel::requestDeleteBook,
                modifier = Modifier.padding(padding),
            )
        }
        (state as? BooksUiState.Content)?.importSheet?.let { sheet ->
            BookImportPreviewSheet(
                state = sheet,
                onNameChange = viewModel::updateImportName,
                onConfirm = viewModel::confirmImport,
                onDismiss = viewModel::cancelImport,
            )
        }
    }
}

/** 主页内容按当前计划、导入动作与词书库分层，业务动作仍交由原 ViewModel。 */
@Composable
private fun BooksContent(
    state: BooksUiState.Content,
    onImport: () -> Unit,
    onCustomGroup: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onStartBook: (Long) -> Unit,
    onDeleteBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentBook = state.books.firstOrNull { it.id == state.currentBookId }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionLabel("当前学习计划")
        }
        item {
            CurrentPlanCard(
                currentBook = currentBook,
                hasCurrentPlan = state.currentBookId != null,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ApplePrimaryAction(
                    text = if (state.isParsingImport) "正在解析文件…" else "导入词书",
                    onClick = onImport,
                    enabled = !state.isParsingImport,
                    leadingContent = {
                        if (state.isParsingImport) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Text(
                    text = "支持 JSON、CSV、TXT",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleUi.colors.secondaryText,
                )
                if (state.currentBookId != null) {
                    AppleGroupedSurface(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        AppleContextActionRow(
                            label = "手动分组",
                            supportingText = "整理当前计划中尚未入组的卡片",
                            onClick = onCustomGroup,
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.GroupAdd,
                                    contentDescription = null,
                                    tint = AppleUi.colors.accent,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = null,
                                    tint = AppleUi.colors.secondaryText,
                                )
                            },
                        )
                    }
                }
            }
        }
        item {
            SectionLabel("词书库")
        }
        if (state.books.isEmpty()) {
            item {
                AppleGroupedSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "还没有词书。你可以从上方导入一份文件。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppleUi.colors.secondaryText,
                    )
                }
            }
        } else {
            items(state.books, key = { it.id }) { book ->
                BookLibraryCard(
                    book = book,
                    isCurrent = book.id == state.currentBookId,
                    isSwitching = book.id == state.switchingBookId,
                    actionsEnabled = state.switchingBookId == null,
                    onOpenDetail = { onOpenDetail(book.id) },
                    onStartBook = { onStartBook(book.id) },
                    onDeleteBook = { onDeleteBook(book.id) },
                )
            }
        }
    }
}

/** 当前计划卡只展示服务端返回的词书名称、已发布卡数量和计划状态。 */
@Composable
private fun CurrentPlanCard(
    currentBook: BookItem?,
    hasCurrentPlan: Boolean,
) {
    AppleGroupedSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = AppleUi.colors.accent.copy(alpha = 0.12f),
                tonalElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (hasCurrentPlan) Icons.Outlined.AutoStories else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = AppleUi.colors.accent,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = currentBook?.name ?: if (hasCurrentPlan) "当前计划" else "尚未建立计划",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppleUi.colors.primaryText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = currentBook?.let { "${it.wordCount} 张已发布卡" }
                        ?: if (hasCurrentPlan) "学习计划已建立" else "从下方词书库选择一本开始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppleUi.colors.secondaryText,
                )
            }
            if (hasCurrentPlan) {
                Surface(
                    shape = CircleShape,
                    color = AppleUi.colors.accent.copy(alpha = 0.12f),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = "进行中",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppleUi.colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** 词书库条目区分当前与可切换状态，并将删除/归档收进低强调菜单。 */
@Composable
private fun BookLibraryCard(
    book: BookItem,
    isCurrent: Boolean,
    isSwitching: Boolean,
    actionsEnabled: Boolean,
    onOpenDetail: () -> Unit,
    onStartBook: () -> Unit,
    onDeleteBook: () -> Unit,
) {
    var menuExpanded by remember(book.id) { mutableStateOf(false) }
    val detailInteractions = remember(book.id) { MutableInteractionSource() }
    AppleGroupedSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 76.dp)
                .applePress(detailInteractions)
                .clickable(
                    role = Role.Button,
                    interactionSource = detailInteractions,
                    indication = null,
                    onClick = onOpenDetail,
                )
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = AppleUi.colors.primaryText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isCurrent) {
                        Text(
                            text = "当前",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppleUi.colors.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text = "${book.wordCount} 张已发布卡 · ${book.sourceLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleUi.colors.secondaryText,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "查看详情",
                modifier = Modifier.size(20.dp),
                tint = AppleUi.colors.secondaryText,
            )
            if (book.canDelete) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = "更多操作",
                            tint = AppleUi.colors.secondaryText,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("删除或归档") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = AppleUi.colors.destructive,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteBook()
                            },
                        )
                    }
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
        HorizontalDivider(color = AppleUi.colors.separator)
        AppleContextActionRow(
            label = when {
                isCurrent -> "当前学习计划"
                isSwitching -> "正在切换计划…"
                else -> "切换到此计划"
            },
            supportingText = if (isCurrent) "学习记录会继续保留" else "原计划和历史进度不会删除",
            onClick = onStartBook,
            enabled = actionsEnabled && !isCurrent,
            leadingContent = {
                if (isSwitching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AppleUi.colors.accent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (isCurrent) Icons.Outlined.CheckCircle else Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        tint = if (isCurrent) AppleUi.colors.accent else AppleUi.colors.secondaryText,
                    )
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = AppleUi.colors.primaryText,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 详情加入学习成功后先消费一次性回传信号，再从服务端刷新当前计划。 */
internal fun handleBookDetailJoinReturn(
    bookId: Long?,
    onConsumed: () -> Unit,
    onRefresh: () -> Unit,
) {
    if (bookId == null) return
    onConsumed()
    onRefresh()
}
