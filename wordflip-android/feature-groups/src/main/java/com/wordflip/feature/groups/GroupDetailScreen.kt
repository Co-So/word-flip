package com.wordflip.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.ui.component.GroupStatsRow
import com.wordflip.core.ui.component.MasteryChip
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.rememberWordFlipToast
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.WordFlipTopBarAction
import com.wordflip.core.ui.component.toChipLevel

/**
 * 分组详情列表模式（REQ-GDETAIL）；只读掌握度 Chip，无手动改态按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: Int,
    groupName: String,
    onNavigateBack: () -> Unit,
    onNavigateToStudy: (StudyNavigation) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupDetailViewModel = viewModel(
        factory = GroupDetailViewModel.Factory(groupId),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
        topBar = {
            WordFlipTopBar(
                title = groupName.ifBlank { "分组详情" },
                onNavigateBack = onNavigateBack,
                actions = {
                    WordFlipTopBarAction(
                        icon = Icons.Outlined.Palette,
                        contentDescription = "污渍模式",
                        onClick = { toast.show("污渍模式功能即将上线") },
                    )
                    WordFlipTopBarAction(
                        icon = Icons.Outlined.PlayArrow,
                        contentDescription = "开始学习",
                        onClick = {
                            viewModel.resolveStudyNavigation()?.let(onNavigateToStudy)
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            GroupDetailUiState.Loading -> {
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
            is GroupDetailUiState.Error -> {
                NetworkErrorView(
                    message = state.message,
                    onRetry = viewModel::loadDetail,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is GroupDetailUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GroupStatsRow(stats = state.group.stats)
                            LinearProgressIndicator(
                                progress = { state.group.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "测验通过 ${(state.group.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.words, key = { it.summary.wordKey }) { item ->
                        GroupWordRow(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupWordRow(item: com.wordflip.core.model.group.GroupWordItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.summary.en,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val subtitle = buildString {
                append(item.summary.cn)
                item.summary.pos?.let { append(" · $it") }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MasteryChip(level = item.mastery.level.toChipLevel())
    }
}
