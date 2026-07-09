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
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.model.settings.QuestionType
import com.wordflip.core.model.settings.QuizLaunchMode
import com.wordflip.core.model.settings.apiValue
import com.wordflip.core.model.settings.label
import com.wordflip.core.model.settings.storageValue
import com.wordflip.core.ui.component.FlipCard
import com.wordflip.core.ui.component.GroupStatsRow
import com.wordflip.core.ui.component.StabilityHeatChip
import com.wordflip.core.ui.component.StabilityHeatRowBackground
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.WordFlipTopBarAction
import com.wordflip.core.ui.component.rememberWordFlipToast
import com.wordflip.feature.settings.SettingsPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 分组详情列表模式（REQ-GDETAIL）；顶栏测验入口按 quizLaunchMode 直开或选题型。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupDetailScreen(
    groupId: Int,
    groupName: String,
    initialStainMode: Boolean = false,
    settingsPreferences: SettingsPreferences,
    onNavigateBack: () -> Unit,
    onNavigateToStudy: (StudyNavigation) -> Unit,
    onNavigateToQuiz: (source: String, groupId: Int, wordLimit: Int, questionTypes: String, launchMode: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var showQuizDialog by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.reload()
        }
    }

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
                        icon = Icons.Outlined.Quiz,
                        contentDescription = "测验",
                        onClick = {
                            // 读 quizLaunchMode：mixed 直开；free_select 弹选题型 Dialog
                            scope.launch {
                                val launchMode = settingsPreferences.quizLaunchModeFlow.first()
                                val limit = settingsPreferences.defaultQuestionLimitFlow.first()
                                when (launchMode) {
                                    QuizLaunchMode.MIXED -> onNavigateToQuiz(
                                        "study",
                                        groupId,
                                        limit,
                                        "",
                                        QuizLaunchMode.MIXED.storageValue(),
                                    )
                                    QuizLaunchMode.FREE_SELECT -> showQuizDialog = true
                                }
                            }
                        },
                    )
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

    if (showQuizDialog) {
        FreeSelectQuizDialog(
            defaultLimit = remember {
                // Dialog 打开时用当前默认题数；异步读取在下方 LaunchedEffect 同步
                10
            },
            settingsPreferences = settingsPreferences,
            onDismiss = { showQuizDialog = false },
            onConfirm = { types, limit ->
                showQuizDialog = false
                onNavigateToQuiz(
                    "study",
                    groupId,
                    limit,
                    types.joinToString(",") { it.apiValue() },
                    QuizLaunchMode.FREE_SELECT.storageValue(),
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeSelectQuizDialog(
    defaultLimit: Int,
    settingsPreferences: SettingsPreferences,
    onDismiss: () -> Unit,
    onConfirm: (types: Set<QuestionType>, limit: Int) -> Unit,
) {
    var selectedTypes by remember {
        mutableStateOf(setOf(QuestionType.DICTATION, QuestionType.CHOICE_EN_CN, QuestionType.CHOICE_CN_EN))
    }
    var limit by remember { mutableFloatStateOf(defaultLimit.toFloat()) }

    LaunchedEffect(settingsPreferences) {
        limit = settingsPreferences.defaultQuestionLimitFlow.first().toFloat()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择测验题型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "题型（可多选）",
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuestionType.entries.forEach { type ->
                        FilterChip(
                            selected = type in selectedTypes,
                            onClick = {
                                selectedTypes = if (type in selectedTypes) {
                                    (selectedTypes - type).ifEmpty { setOf(type) }
                                } else {
                                    selectedTypes + type
                                }
                            },
                            label = { Text(type.label()) },
                        )
                    }
                }
                Text(
                    text = "题数：${limit.toInt()}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = limit,
                    onValueChange = { limit = it },
                    valueRange = 5f..50f,
                    steps = 8,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedTypes, limit.toInt()) },
                enabled = selectedTypes.isNotEmpty(),
            ) {
                Text("开始测验")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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
    // 热力优先 progress.displayHeatLevel；薄弱角标由 Chip 按 heatDisplayMode 解析
    StabilityHeatRowBackground(
        heatLevel = item.displayHeatLevel,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
            StabilityHeatChip(mastery = item.mastery, progress = item.progress)
        }
    }
}
