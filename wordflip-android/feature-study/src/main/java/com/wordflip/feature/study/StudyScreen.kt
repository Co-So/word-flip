package com.wordflip.feature.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
                        onPlaceholderAction = toast::show,
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
    onShuffle: () -> Unit,
    onFlipAll: () -> Unit,
    onCardClick: (String) -> Unit,
    onCardLongClick: (String) -> Unit,
) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        StudyToolbar(
            allFlippedToBack = state.allFlippedToBack,
            isShuffling = state.isShuffling,
            onShuffle = onShuffle,
            onFlipAll = onFlipAll,
        )
        AnimatedVisibility(
            visible = !state.isShuffling,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = state.orderedWords,
                    key = { it.wordKey },
                ) { word ->
                    val flipped = state.flipStates[word.wordKey] == true
                    FlipCard(
                        en = word.en,
                        cn = word.cn,
                        ph = word.ph,
                        pos = word.pos,
                        stainSeed = word.stain.seed,
                        stainHidden = word.stain.hidden,
                        isFlipped = flipped,
                        onClick = { onCardClick(word.wordKey) },
                        onLongClick = { onCardLongClick(word.wordKey) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        if (state.isShuffling) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
