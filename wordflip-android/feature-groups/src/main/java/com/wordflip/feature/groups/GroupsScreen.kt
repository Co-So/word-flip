package com.wordflip.feature.groups

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wordflip.core.ui.component.EmptyStateView
import com.wordflip.core.ui.component.GroupListRow
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.rememberWordFlipToast

/**
 * 分组管理页（REQ-GROUP-1~5）；支持搜索与筛选，组多时用紧凑行减少滚动距离。
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
        topBar = { WordFlipTopBar(title = "分组") },
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState) {
            GroupsUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is GroupsUiState.Error -> {
                NetworkErrorView(
                    message = state.message,
                    onRetry = viewModel::loadGroups,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is GroupsUiState.Content -> {
                if (state.groups.isEmpty()) {
                    EmptyStateView(
                        title = "暂无分组",
                        message = "请在词书页保存设置后自动生成分组",
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    GroupsListContent(
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
    }
}

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
            .padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索组名，如「第29组」") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                },
            )
        }
        item(key = "status_filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GroupStatusFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.statusFilter == filter,
                        onClick = { onStatusFilterChange(filter) },
                        label = {
                            Text("${filter.label} (${state.statusCounts[filter] ?: 0})")
                        },
                    )
                }
            }
        }
        if (state.hasCustomGroups) {
            item(key = "source_filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GroupSourceFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.sourceFilter == filter,
                            onClick = { onSourceFilterChange(filter) },
                            label = { Text(filter.label) },
                        )
                    }
                }
            }
        }
        item(key = "summary") {
            Text(
                text = buildGroupSummary(state, filtered.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
        if (filtered.isEmpty()) {
            item(key = "empty_filter") {
                EmptyStateView(
                    title = "没有匹配的分组",
                    message = "试试调整筛选条件或搜索关键词",
                )
            }
        } else {
            items(filtered, key = { it.id }) { group ->
                GroupListRow(
                    group = group,
                    onClick = { onNavigateToGroupDetail(group.id, group.name) },
                    onSnapshotClick = { onSnapshotClick(group.id, group.name) },
                    onStainClick = { onStainClick(group.id, group.name) },
                )
            }
        }
    }
}

private fun buildGroupSummary(state: GroupsUiState.Content, filteredCount: Int): String {
    return if (filteredCount == state.groups.size) {
        "共 ${state.groups.size} 组"
    } else {
        "共 ${state.groups.size} 组，当前显示 $filteredCount 组"
    }
}
