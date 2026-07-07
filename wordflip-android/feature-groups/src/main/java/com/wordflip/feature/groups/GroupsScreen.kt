package com.wordflip.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordflip.core.ui.component.EmptyStateView
import com.wordflip.core.ui.component.GroupCard
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.rememberWordFlipToast

/**
 * 分组管理页（REQ-GROUP-1~5）；卡拍/污渍入口导航至子页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onNavigateToGroupDetail: (Int, String) -> Unit,
    onNavigateToSnapshot: (Int, String) -> Unit,
    onNavigateToStainMode: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupsViewModel = viewModel(),
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.groups, key = { it.id }) { group ->
                            GroupCard(
                                group = group,
                                onClick = { onNavigateToGroupDetail(group.id, group.name) },
                                onSnapshotClick = { viewModel.onSnapshotClick(group.id, group.name) },
                                onStainClick = { viewModel.onStainClick(group.id, group.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}
