package com.wordflip.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.ui.component.FlipCard
import com.wordflip.core.ui.component.GroupStatsRow
import com.wordflip.core.ui.component.MasteryChip
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.WordFlipTopBarAction
import com.wordflip.core.ui.component.rememberWordFlipToast
import com.wordflip.core.ui.component.toChipLevel

/**
 * 分组详情列表模式（REQ-GDETAIL）；污渍模式 P3-A10。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupDetailScreen(
    groupId: Int,
    groupName: String,
    initialStainMode: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToStudy: (StudyNavigation) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupDetailViewModel = viewModel(
        factory = GroupDetailViewModel.Factory(groupId, initialStainMode),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupDetailUiEvent.Toast -> toast.show(event.message)
            }
        }
    }

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
                        onClick = viewModel::toggleStainMode,
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
                    onRetry = viewModel::reload,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is GroupDetailUiState.Content -> {
                if (state.stainMode) {
                    GroupStainModeContent(
                        state = state,
                        modifier = Modifier.padding(innerPadding),
                        onToggleType = viewModel::toggleStainType,
                        onBatchGenerate = viewModel::batchGenerateStains,
                        onToggleHidden = viewModel::toggleStainsHidden,
                        onRegenerate = viewModel::regenerateStain,
                    )
                } else {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupStainModeContent(
    state: GroupDetailUiState.Content,
    onToggleType: (StainType) -> Unit,
    onBatchGenerate: () -> Unit,
    onToggleHidden: () -> Unit,
    onRegenerate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StainType.entries.forEach { type ->
                FilterChip(
                    selected = type in state.selectedStainTypes,
                    onClick = { onToggleType(type) },
                    label = { Text(stainTypeLabel(type)) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onBatchGenerate, modifier = Modifier.weight(1f)) {
                Text("一键生成")
            }
            OutlinedButton(onClick = onToggleHidden, modifier = Modifier.weight(1f)) {
                Text(if (state.stainsHidden) "显示污渍" else "隐藏污渍")
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.stainCards, key = { it.wordKey }) { word ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlipCard(
                        en = word.en,
                        cn = word.cn,
                        ph = word.ph,
                        pos = word.pos,
                        wordKey = word.wordKey,
                        stainSeed = word.stain.seed,
                        stainHidden = word.stain.hidden,
                        stainConfig = word.stain.config,
                        isFlipped = false,
                        onClick = { onRegenerate(word.wordKey) },
                        onLongClick = { onRegenerate(word.wordKey) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = word.en,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun stainTypeLabel(type: StainType): String = when (type) {
    StainType.COFFEE -> "咖啡"
    StainType.INK -> "墨水"
    StainType.HIGHLIGHT -> "荧光"
    StainType.CRAYON -> "蜡笔"
    StainType.RANDOM_LINE -> "线条"
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
