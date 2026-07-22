package com.wordflip.feature.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.model.study.WordSummary
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.ApplePageTitle
import com.wordflip.core.ui.apple.ApplePrimaryAction
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.rememberWordFlipToast

/** 词书详情：以大标题和可滚动索引展示词条，学习计划仍由原回调切换。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    onNavigateBack: () -> Unit,
    onJoinLearning: (bookId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()
    var pendingDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BookDetailEvent.Toast -> toast.show(event.message)
                is BookDetailEvent.ConfirmDelete -> pendingDelete = event.bookId to event.bookName
                BookDetailEvent.Deleted -> {
                    toast.show("词书已删除")
                    onNavigateBack()
                }
                is BookDetailEvent.JoinLearning -> onJoinLearning(event.bookId)
            }
        }
    }

    pendingDelete?.let { (_, bookName) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除或归档词书") },
            text = { Text("确定处理「$bookName」？历史计划存在时只会归档，已入组的单词仍会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmDelete()
                        pendingDelete = null
                    },
                ) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    val canDelete = (uiState as? BookDetailUiState.Content)?.book?.canDelete == true
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppleUi.colors.canvas,
        topBar = {
            TopAppBar(
                title = { Text("词书详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (canDelete) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
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
                                        viewModel.requestDelete()
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppleUi.colors.canvas,
                    titleContentColor = AppleUi.colors.primaryText,
                ),
            )
        },
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
        bottomBar = {
            val content = uiState as? BookDetailUiState.Content
            if (content != null) {
                BookDetailBottomAction(
                    selected = content.book.selected,
                    isJoiningLearning = content.isJoiningLearning,
                    onJoinLearning = viewModel::joinLearning,
                )
            }
        },
    ) { innerPadding ->
        when (val state = uiState) {
            BookDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AppleUi.colors.accent)
                }
            }
            is BookDetailUiState.Error -> {
                NetworkErrorView(
                    message = state.message,
                    onRetry = viewModel::refresh,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is BookDetailUiState.Content -> {
                BookDetailContent(
                    state = state,
                    onLoadMore = viewModel::loadMore,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

/** 固定底部区域区分当前计划与可加入状态，不改变 joinLearning 回调。 */
@Composable
private fun BookDetailBottomAction(
    selected: Boolean,
    isJoiningLearning: Boolean,
    onJoinLearning: () -> Unit,
) {
    Surface(
        color = AppleUi.colors.glass,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (selected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = AppleUi.colors.accent,
                    )
                    Text(
                        text = "已在当前学习计划中",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = AppleUi.colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                ApplePrimaryAction(
                    text = if (isJoiningLearning) "正在加入…" else "加入学习计划",
                    onClick = onJoinLearning,
                    enabled = !isJoiningLearning,
                    leadingContent = if (isJoiningLearning) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun BookDetailContent(
    state: BookDetailUiState.Content,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.words.size - 3 && !state.endReached
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ApplePageTitle(
                    title = state.book.name,
                    subtitle = "${state.book.wordCount} 张已发布卡 · ${state.book.sourceLabel()}",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailBadge(
                        text = state.book.sourceLabel(),
                        emphasized = false,
                    )
                    DetailBadge(
                        text = if (state.book.selected) "当前计划" else "可加入学习",
                        emphasized = state.book.selected,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "词条索引",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppleUi.colors.primaryText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "已载入 ${state.words.size} / ${state.book.wordCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppleUi.colors.secondaryText,
                    )
                }
            }
        }
        if (state.words.isEmpty() && state.endReached) {
            item {
                AppleGroupedSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "这本词书暂时没有可展示的词条。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppleUi.colors.secondaryText,
                    )
                }
            }
        } else {
            itemsIndexed(
                items = state.words,
                key = { _, word -> word.wordKey },
            ) { index, word ->
                BookWordIndexRow(index = index + 1, word = word)
            }
        }
        if (!state.endReached) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AppleUi.colors.accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

/** 每个索引项保持清晰的英文主信息、中文释义和序号层级。 */
@Composable
private fun BookWordIndexRow(
    index: Int,
    word: WordSummary,
) {
    AppleGroupedSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = AppleUi.colors.accent.copy(alpha = 0.1f),
                tonalElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.size(34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppleUi.colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = word.en,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppleUi.colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                word.cn?.takeIf { it.isNotBlank() }?.let { meaning ->
                    Text(
                        text = meaning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppleUi.colors.secondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBadge(
    text: String,
    emphasized: Boolean,
) {
    Surface(
        shape = CircleShape,
        color = if (emphasized) AppleUi.colors.accent.copy(alpha = 0.12f) else AppleUi.colors.elevatedSurface,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (emphasized) AppleUi.colors.accent else AppleUi.colors.secondaryText,
        )
    }
}
