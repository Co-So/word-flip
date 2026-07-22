package com.wordflip.feature.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wordflip.core.model.group.GroupDetail
import com.wordflip.core.model.group.GroupSource
import com.wordflip.core.model.group.GroupStatus
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.ApplePageTitle
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.apple.applePress
import com.wordflip.core.ui.component.EmptyStateView
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.rememberWordFlipToast

/**
 * 分组管理页（REQ-GROUP-1~5）；搜索与筛选置顶，卡片主区域和次操作保持独立点击入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onNavigateToGroupDetail: (Int, String) -> Unit,
    onNavigateToSnapshot: (Int, String) -> Unit,
    onNavigateToStainMode: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadGroups()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupsUiEvent.Toast -> toast.show(event.message)
                is GroupsUiEvent.NavigateToSnapshot -> onNavigateToSnapshot(event.groupId, event.groupName)
                is GroupsUiEvent.NavigateToStainMode -> onNavigateToStainMode(event.groupId, event.groupName)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppleUi.colors.canvas,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState) {
            GroupsUiState.Loading -> GroupsLoadingContent(
                modifier = Modifier.padding(innerPadding),
            )
            is GroupsUiState.Error -> GroupsErrorContent(
                message = state.message,
                onRetry = viewModel::loadGroups,
                modifier = Modifier.padding(innerPadding),
            )
            is GroupsUiState.Content -> GroupsListContent(
                state = state,
                innerPadding = innerPadding,
                onStatusFilterChange = viewModel::setStatusFilter,
                onSourceFilterChange = viewModel::setSourceFilter,
                onSearchQueryChange = viewModel::setSearchQuery,
                onNavigateToGroupDetail = onNavigateToGroupDetail,
                onSnapshotClick = viewModel::onSnapshotClick,
                onStainClick = viewModel::onStainClick,
            )
        }
    }
}

/** 加载态保留页面定位信息，并在安全区内居中展示进度。 */
@Composable
private fun GroupsLoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ApplePageTitle(title = "分组", subtitle = "整理并继续你的学习计划")
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppleUi.colors.accent)
        }
    }
}

/** 网络错误仍提供原重试入口，同时维持明暗主题下的页面层级。 */
@Composable
private fun GroupsErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ApplePageTitle(title = "分组", subtitle = "整理并继续你的学习计划")
        NetworkErrorView(
            message = message,
            onRetry = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 列表只重排展示层，筛选与导航仍通过既有回调交给 ViewModel 和导航层。 */
@Composable
private fun GroupsListContent(
    state: GroupsUiState.Content,
    innerPadding: PaddingValues,
    onStatusFilterChange: (GroupStatusFilter) -> Unit,
    onSourceFilterChange: (GroupSourceFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onNavigateToGroupDetail: (Int, String) -> Unit,
    onSnapshotClick: (Int, String) -> Unit,
    onStainClick: (Int, String) -> Unit,
) {
    val filtered = state.filteredGroups
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            ApplePageTitle(
                title = "分组",
                subtitle = if (state.groups.isEmpty()) "还没有可学习的分组" else buildGroupSummary(state, filtered.size),
            )
        }
        if (state.groups.isEmpty()) {
            item(key = "empty") {
                AppleGroupedSurface(modifier = Modifier.fillMaxWidth()) {
                    EmptyStateView(
                        title = "暂无分组",
                        message = "请在词书页保存设置后自动生成分组",
                    )
                }
            }
        } else {
            item(key = "search") {
                GroupSearchField(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChange,
                )
            }
            item(key = "status_filters") {
                CompactFilterRow {
                    GroupStatusFilter.entries.forEach { filter ->
                        CompactFilterChip(
                            selected = state.statusFilter == filter,
                            onClick = { onStatusFilterChange(filter) },
                            label = "${filter.label} (${state.statusCounts[filter] ?: 0})",
                        )
                    }
                }
            }
            if (state.hasCustomGroups) {
                item(key = "source_filters") {
                    CompactFilterRow {
                        GroupSourceFilter.entries.forEach { filter ->
                            CompactFilterChip(
                                selected = state.sourceFilter == filter,
                                onClick = { onSourceFilterChange(filter) },
                                label = filter.label,
                            )
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty_filter") {
                    AppleGroupedSurface(modifier = Modifier.fillMaxWidth()) {
                        EmptyStateView(
                            title = "没有匹配的分组",
                            message = "试试调整筛选条件或搜索关键词",
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { group ->
                    GroupProgressCard(
                        group = group,
                        onClick = { onNavigateToGroupDetail(group.id, group.name) },
                        onSnapshotClick = { onSnapshotClick(group.id, group.name) },
                        onStainClick = { onStainClick(group.id, group.name) },
                    )
                }
            }
        }
    }
}

/** 搜索框使用分组表面和语义描边，不依赖固定浅色。 */
@Composable
private fun GroupSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = { Text("搜索组名，如“第29组”") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AppleUi.colors.groupedSurface,
            unfocusedContainerColor = AppleUi.colors.groupedSurface,
            focusedBorderColor = AppleUi.colors.accent,
            unfocusedBorderColor = AppleUi.colors.separator,
            focusedTextColor = AppleUi.colors.primaryText,
            unfocusedTextColor = AppleUi.colors.primaryText,
            focusedPlaceholderColor = AppleUi.colors.secondaryText,
            unfocusedPlaceholderColor = AppleUi.colors.secondaryText,
        ),
    )
}

@Composable
private fun CompactFilterRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** 筛选标签保持紧凑视觉，同时提供至少 48dp 的可点高度。 */
@Composable
private fun CompactFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        label = { Text(label) },
    )
}

/**
 * 进度卡主区域进入详情；底部卡拍和污渍各自拥有独立点击区域，避免误触发详情。
 */
@Composable
private fun GroupProgressCard(
    group: GroupDetail,
    onClick: () -> Unit,
    onSnapshotClick: () -> Unit,
    onStainClick: () -> Unit,
) {
    val detailInteractions = remember(group.id) { MutableInteractionSource() }
    AppleGroupedSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .applePress(detailInteractions)
                .clickable(
                    role = Role.Button,
                    interactionSource = detailInteractions,
                    indication = null,
                    onClick = onClick,
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = AppleUi.colors.primaryText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupStatusPill(group.status)
                        Text(
                            text = if (group.source == GroupSource.CUSTOM) "自定义" else "自动分组",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppleUi.colors.secondaryText,
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = AppleUi.colors.accent.copy(alpha = 0.1f),
                    tonalElevation = 0.dp,
                ) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = "查看详情",
                            tint = AppleUi.colors.accent,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${group.stats.total} 个单词",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppleUi.colors.secondaryText,
                )
                Text(
                    text = "测验通过 ${(group.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppleUi.colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = { group.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = AppleUi.colors.accent,
                trackColor = AppleUi.colors.separator,
            )
        }
        HorizontalDivider(color = AppleUi.colors.separator)
        Row(modifier = Modifier.fillMaxWidth()) {
            GroupCardContextAction(
                label = "卡拍",
                icon = Icons.Outlined.PhotoCamera,
                onClick = onSnapshotClick,
                modifier = Modifier.weight(1f),
            )
            GroupCardContextAction(
                label = "污渍",
                icon = Icons.Outlined.Palette,
                onClick = onStainClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 卡片次操作不包裹在主点击区域内，保持导航事件彼此独立。 */
@Composable
private fun GroupCardContextAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = AppleUi.colors.secondaryText),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.size(7.dp))
        Text(text = label, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GroupStatusPill(status: GroupStatus) {
    val active = status != GroupStatus.NOT_STARTED
    Surface(
        shape = CircleShape,
        color = if (active) AppleUi.colors.accent.copy(alpha = 0.12f) else AppleUi.colors.separator,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = when (status) {
                GroupStatus.COMPLETED -> "已完成"
                GroupStatus.LEARNING -> "学习中"
                GroupStatus.NOT_STARTED -> "未开始"
            },
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) AppleUi.colors.accent else AppleUi.colors.secondaryText,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun buildGroupSummary(state: GroupsUiState.Content, filteredCount: Int): String {
    return if (filteredCount == state.groups.size) {
        "共 ${state.groups.size} 组"
    } else {
        "共 ${state.groups.size} 组，当前显示 $filteredCount 组"
    }
}
