package com.wordflip.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wordflip.core.model.group.GroupSource
import com.wordflip.core.model.group.GroupStatus
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.model.settings.QuestionType
import com.wordflip.core.model.settings.QuizLaunchMode
import com.wordflip.core.model.settings.apiValue
import com.wordflip.core.model.settings.label
import com.wordflip.core.model.settings.storageValue
import com.wordflip.core.ui.apple.AppleContextActionRow
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.ApplePageTitle
import com.wordflip.core.ui.apple.ApplePrimaryAction
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.component.EmptyStateView
import com.wordflip.core.ui.component.FlipCard
import com.wordflip.core.ui.component.GroupStatsRow
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.StabilityHeatChip
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.rememberWordFlipToast
import com.wordflip.feature.settings.SettingsPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 分组详情页（REQ-GDETAIL）：学习为主操作，测验和污渍编辑保留为独立次操作。
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

    // 继续读取既有测验偏好：混合模式直开，自由选择模式进入题型弹窗。
    val onQuizClick: () -> Unit = {
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
    }

    val stainMode = (uiState as? GroupDetailUiState.Content)?.stainMode == true
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppleUi.colors.canvas,
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
        topBar = {
            WordFlipTopBar(
                title = if (stainMode) "污渍编辑" else "分组详情",
                onNavigateBack = onNavigateBack,
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            GroupDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AppleUi.colors.accent)
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
                        onExitStainMode = viewModel::toggleStainMode,
                        onToggleType = viewModel::toggleStainType,
                        onBatchGenerate = viewModel::batchGenerateStains,
                        onToggleHidden = viewModel::toggleStainsHidden,
                        onRegenerate = viewModel::regenerateStain,
                    )
                } else {
                    GroupDetailContent(
                        state = state,
                        modifier = Modifier.padding(innerPadding),
                        onStudyClick = {
                            viewModel.resolveStudyNavigation()?.let(onNavigateToStudy)
                        },
                        onQuizClick = onQuizClick,
                        onStainClick = viewModel::toggleStainMode,
                    )
                }
            }
        }
    }

    if (showQuizDialog) {
        FreeSelectQuizDialog(
            defaultLimit = remember {
                // 弹窗先使用原有默认值，再由偏好流同步用户设置。
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

/** 普通详情按概览、主要动作、次操作和单词列表建立清晰层级。 */
@Composable
private fun GroupDetailContent(
    state: GroupDetailUiState.Content,
    onStudyClick: () -> Unit,
    onQuizClick: () -> Unit,
    onStainClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            ApplePageTitle(
                title = state.group.name,
                subtitle = "${groupSourceLabel(state.group.source)} · ${groupStatusLabel(state.group.status)}",
            )
        }
        item(key = "overview") {
            GroupOverviewCard(state)
        }
        item(key = "study") {
            ApplePrimaryAction(
                text = "开始学习",
                onClick = onStudyClick,
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                },
            )
        }
        item(key = "secondary_actions") {
            AppleGroupedSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp),
            ) {
                AppleContextActionRow(
                    label = "开始测验",
                    supportingText = "按你的测验偏好进入题型",
                    onClick = onQuizClick,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Quiz,
                            contentDescription = null,
                            tint = AppleUi.colors.accent,
                        )
                    },
                )
                HorizontalDivider(color = AppleUi.colors.separator)
                AppleContextActionRow(
                    label = "污渍编辑",
                    supportingText = "为本组卡片制作或隐藏污渍",
                    onClick = onStainClick,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = AppleUi.colors.secondaryText,
                        )
                    },
                )
            }
        }
        item(key = "words_title") {
            SectionLabel(text = "组内单词 · ${state.words.size}")
        }
        if (state.words.isEmpty()) {
            item(key = "words_empty") {
                AppleGroupedSurface(modifier = Modifier.fillMaxWidth()) {
                    EmptyStateView(
                        title = "暂无单词",
                        message = "这个分组暂时没有可显示的单词",
                    )
                }
            }
        } else {
            items(state.words, key = { it.summary.wordKey }) { item ->
                GroupWordRow(item = item)
            }
        }
    }
}

/** 概览只展示服务端返回的组统计与进度，不在客户端推导学习状态。 */
@Composable
private fun GroupOverviewCard(state: GroupDetailUiState.Content) {
    AppleGroupedSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "学习概览",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppleUi.colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "共 ${state.group.stats.total} 个单词",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleUi.colors.secondaryText,
                )
            }
            Surface(
                shape = CircleShape,
                color = AppleUi.colors.accent.copy(alpha = 0.12f),
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = "${(state.group.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = AppleUi.colors.accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        GroupStatsRow(stats = state.group.stats)
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { state.group.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp),
            color = AppleUi.colors.accent,
            trackColor = AppleUi.colors.separator,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "测验通过进度",
            style = MaterialTheme.typography.bodySmall,
            color = AppleUi.colors.secondaryText,
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
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
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

/**
 * 污渍编辑与普通详情使用不同的状态横幅，并提供显眼、可随时触达的退出入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupStainModeContent(
    state: GroupDetailUiState.Content,
    onExitStainMode: () -> Unit,
    onToggleType: (StainType) -> Unit,
    onBatchGenerate: () -> Unit,
    onToggleHidden: () -> Unit,
    onRegenerate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ApplePageTitle(
                title = "污渍编辑",
                subtitle = state.group.name,
            )
            StainEditingBanner(onExitStainMode = onExitStainMode)
            AppleGroupedSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
            ) {
                Text(
                    text = "污渍类型",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppleUi.colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StainType.entries.forEach { type ->
                        FilterChip(
                            selected = type in state.selectedStainTypes,
                            onClick = { onToggleType(type) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            label = { Text(stainTypeLabel(type)) },
                        )
                    }
                }
            }
            ApplePrimaryAction(
                text = "为全组生成污渍",
                onClick = onBatchGenerate,
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            AppleGroupedSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp),
            ) {
                AppleContextActionRow(
                    label = if (state.stainsHidden) "显示全组污渍" else "隐藏全组污渍",
                    supportingText = if (state.stainsHidden) "当前预览已隐藏" else "当前预览正在显示",
                    onClick = onToggleHidden,
                    leadingContent = {
                        Icon(
                            imageVector = if (state.stainsHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = null,
                            tint = AppleUi.colors.secondaryText,
                        )
                    },
                )
            }
            Text(
                text = "轻触任一卡片可单独重新生成",
                style = MaterialTheme.typography.bodySmall,
                color = AppleUi.colors.secondaryText,
            )
        }
        if (state.stainCards.isEmpty()) {
            AppleGroupedSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                EmptyStateView(
                    title = "暂无卡片",
                    message = "这个分组暂时没有可编辑的卡片",
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(state.stainCards, key = { it.wordKey }) { word ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                            color = AppleUi.colors.primaryText,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 编辑状态横幅同时解释当前模式，并提供不依赖返回键的退出路径。 */
@Composable
private fun StainEditingBanner(onExitStainMode: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppleUi.colors.accent.copy(alpha = 0.12f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = AppleUi.colors.accent.copy(alpha = 0.14f),
                    tonalElevation = 0.dp,
                ) {
                    Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = AppleUi.colors.accent,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "编辑模式已开启",
                        style = MaterialTheme.typography.titleSmall,
                        color = AppleUi.colors.primaryText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "卡片点击会重新生成污渍",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleUi.colors.secondaryText,
                    )
                }
            }
            OutlinedButton(
                onClick = onExitStainMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleUi.colors.accent),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.size(7.dp))
                Text("退出污渍编辑", fontWeight = FontWeight.SemiBold)
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

private fun groupSourceLabel(source: GroupSource): String = when (source) {
    GroupSource.AUTO -> "自动分组"
    GroupSource.CUSTOM -> "自定义分组"
}

private fun groupStatusLabel(status: GroupStatus): String = when (status) {
    GroupStatus.NOT_STARTED -> "未开始"
    GroupStatus.LEARNING -> "学习中"
    GroupStatus.COMPLETED -> "已完成"
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

/** 单词行仅呈现服务端给出的释义与热力档，不写入任何学习状态。 */
@Composable
private fun GroupWordRow(item: com.wordflip.core.model.group.GroupWordItem) {
    AppleGroupedSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.summary.en,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppleUi.colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    append(item.summary.displayMeaning().ifBlank { "暂无释义" })
                    item.summary.pos?.let { append(" · $it") }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleUi.colors.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StabilityHeatChip(heatLevel = item.displayHeatLevel)
        }
    }
}
