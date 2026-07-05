package com.wordflip.feature.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordflip.core.ui.component.FlipCard
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.WordFlipTopBarAction
import com.wordflip.core.ui.component.rememberWordFlipToast
import com.wordflip.feature.settings.SettingsPreferences

/**
 * 卡片学习页（REQ-STUDY-1~3）：TopBar + 工具栏 + 2 列 FlipCard 网格。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    groupId: Int,
    groupName: String,
    wordCount: Int,
    settingsPreferences: SettingsPreferences,
    onNavigateBack: () -> Unit,
    onNavigateToQuiz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) { ShuffleMotion.isReduceMotionEnabled(context) }
    val viewModel: StudyViewModel = viewModel(
        key = "study-$groupId",
        factory = StudyViewModel.Factory(context, groupId),
    )
    val uiState by viewModel.uiState.collectAsState()
    // 与 MainActivity 同一 DataStore 实例，Compose 层实时读开关
    val autoSpeak by settingsPreferences.autoSpeakFlow.collectAsState(initial = true)
    val (snackbarHostState, toast) = rememberWordFlipToast()
    val tts = remember { StudyTtsHelper(context) }
    val isDetailSpeaking by tts.isDetailSpeaking.collectAsState()
    var displayRate by remember { mutableStateOf(1.0f) }

    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is StudyUiEvent.Toast -> toast.show(event.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
        topBar = {
            WordFlipTopBar(
                title = groupName.ifBlank { "学习" },
                onNavigateBack = onNavigateBack,
                actions = {
                    // REQ-STUDY-1：测验入口占位（P2）
                    WordFlipTopBarAction(
                        icon = Icons.Outlined.Spellcheck,
                        contentDescription = "默写测验",
                        onClick = onNavigateToQuiz,
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = uiState) {
                StudyUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is StudyUiState.Error -> {
                    NetworkErrorView(
                        message = state.message,
                        onRetry = { /* reload via new ViewModel instance not trivial; ignore for mock */ },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                is StudyUiState.Content -> {
                    StudyContent(
                        state = state,
                        reduceMotion = reduceMotion,
                        onShuffle = viewModel::shuffle,
                        onFlipAll = {
                            viewModel.flipAll(toBack = !state.allFlippedToBack)
                        },
                        onCardClick = { wordKey ->
                            if (state.detailWordKey == null) {
                                viewModel.toggleFlip(wordKey)
                                // 对齐 v5：每次点击翻转时朗读；开关关闭则静音
                                if (autoSpeak) {
                                    viewModel.currentWord(wordKey)?.let { tts.speakForCard(it.en) }
                                }
                            }
                        },
                        onCardLongClick = viewModel::openDetail,
                    )
                    StudyDetailSheet(
                        word = viewModel.detailWord(),
                        visible = state.detailWordKey != null,
                        speechRate = displayRate,
                        isDetailSpeaking = isDetailSpeaking,
                        onDismiss = viewModel::closeDetail,
                        onSpeak = {
                            viewModel.detailWord()?.let { tts.speakForDetail(it.en) }
                        },
                        onRateDown = {
                            tts.adjustDetailRate(-0.25f)
                            displayRate = tts.detailRate
                        },
                        onRateUp = {
                            tts.adjustDetailRate(0.25f)
                            displayRate = tts.detailRate
                        },
                        onChangeStain = viewModel::changeStain,
                        onHideStain = viewModel::hideStain,
                        onToggleShowCnOnImage = viewModel::toggleShowCnOnImage,
                    )
                    StudyGuideOverlay(
                        visible = state.showGuide,
                        onDismiss = viewModel::dismissGuide,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyContent(
    state: StudyUiState.Content,
    reduceMotion: Boolean,
    onShuffle: (ShuffleViewportAnchor) -> Unit,
    onFlipAll: () -> Unit,
    onCardClick: (String) -> Unit,
    onCardLongClick: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val horizontalPadding = 16.dp
    val verticalPadding = 8.dp
    val gap = 12.dp

    // 打乱动画结束后，平滑滚动回顶部，让用户从第一屏开始看新顺序
    LaunchedEffect(state.isShuffling, state.shuffleSettling) {
        if (!state.isShuffling && !state.shuffleSettling && state.shuffleEpoch > 0) {
            gridState.animateScrollToItem(index = 0)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false },
    ) {
        val contentWidth = maxWidth - horizontalPadding * 2
        val cardWidth = (contentWidth - gap) / 2
        val cardHeight = cardWidth * 4.2f / 3f
        val gridSpec = remember(maxWidth, cardWidth) {
            with(density) {
                ShuffleGridSpec(
                    cardWidthPx = cardWidth.toPx(),
                    cardHeightPx = cardHeight.toPx(),
                    gapPx = gap.toPx(),
                )
            }
        }

        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
            StudyToolbar(
                allFlippedToBack = state.allFlippedToBack,
                isShuffling = state.isShuffling,
                onShuffle = {
                    val layoutInfo = gridState.layoutInfo
                    val measuredByWordKey = layoutInfo.visibleItemsInfo.mapNotNull { item ->
                        val word = state.orderedWords.getOrNull(item.index) ?: return@mapNotNull null
                        word.wordKey to (
                            item.offset.x + item.size.width / 2f to
                                item.offset.y + item.size.height / 2f
                            )
                    }.toMap()
                    val anchor = with(density) {
                        val base = ShuffleViewportAnchor(
                            centerXPx = layoutInfo.viewportSize.width / 2f,
                            centerYPx = layoutInfo.viewportStartOffset.toFloat() +
                                layoutInfo.viewportSize.height / 2f,
                            contentPaddingLeftPx = horizontalPadding.toPx(),
                            contentPaddingTopPx = verticalPadding.toPx(),
                            viewportStartOffsetPx = layoutInfo.viewportStartOffset,
                            measuredCentersByWordKey = measuredByWordKey,
                        )
                        base.copy(
                            centersByIndexAtStart = ShuffleMotion.precomputeCentersByIndex(
                                cardCount = state.orderedWords.size,
                                spec = gridSpec,
                                anchor = base,
                            ),
                        )
                    }
                    onShuffle(anchor)
                },
                onFlipAll = onFlipAll,
            )
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { clip = false },
                userScrollEnabled = !state.isShuffling,
            ) {
                itemsIndexed(
                    items = state.orderedWords,
                    key = { _, word -> word.wordKey },
                ) { index, word ->
                    val flipped = state.flipStates[word.wordKey] == true
                    Box(
                        modifier = Modifier
                            .shuffleCardMotion(
                                wordKey = word.wordKey,
                                phase = state.shufflePhase,
                                shuffleEpoch = state.shuffleEpoch,
                                index = index,
                                motion = state.shuffleMotions[word.wordKey],
                                gridSpec = gridSpec,
                                viewportAnchor = state.shuffleViewportAnchor,
                                shuffleSettling = state.shuffleSettling,
                                reduceMotion = reduceMotion,
                                dealStartOffset = state.shuffleDealStartOffsets[word.wordKey],
                            ),
                    ) {
                        FlipCard(
                            en = word.en,
                            cn = word.cn,
                            ph = word.ph,
                            pos = word.pos,
                            stainSeed = word.stain.seed,
                            stainHidden = word.stain.hidden,
                            hasImage = word.image.hasImage,
                            showCnOnImage = word.image.showCnOnImage,
                            isFlipped = flipped,
                            onClick = { onCardClick(word.wordKey) },
                            onLongClick = { onCardLongClick(word.wordKey) },
                            modifier = Modifier,
                            interactionEnabled = !state.isShuffling,
                        )
                    }
                }
            }
        }
    }
}
