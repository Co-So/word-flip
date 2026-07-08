package com.wordflip.feature.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.zIndex
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.image.ImageEditorScreen
import com.wordflip.core.image.rememberImagePickerLaunchers
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.ui.component.WordFlipBottomSheet
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
    val viewModel: StudyViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    // 与 MainActivity 同一 DataStore 实例，Compose 层实时读开关
    val autoSpeak by settingsPreferences.autoSpeakFlow.collectAsState(initial = true)
    val (snackbarHostState, toast) = rememberWordFlipToast()
    val pickLaunchers = rememberImagePickerLaunchers(
        onImagePicked = viewModel::onImagePickedFromLauncher,
        onCameraDenied = { toast.show("需要相机权限才能拍照") },
    )
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

    val contentState = uiState as? StudyUiState.Content
    val editorWord = contentState?.editorWordKey?.let { viewModel.currentWord(it) }
    val isEditorOpen = editorWord != null && editorWord.image.hasImage

    /** 子层优先：选图 Sheet → 编辑器 → 详情抽屉 → 退出学习页 */
    val handleNavigateBack: () -> Unit = {
        when {
            contentState?.imagePickSheetWordKey != null -> viewModel.closeImagePickSheet()
            isEditorOpen -> viewModel.closeImageEditor()
            contentState?.detailWordKey != null -> viewModel.closeDetail()
            else -> viewModel.leaveStudy(onNavigateBack)
        }
    }

    BackHandler(enabled = contentState?.imagePickSheetWordKey != null) {
        viewModel.closeImagePickSheet()
    }
    BackHandler(enabled = isEditorOpen) {
        viewModel.closeImageEditor()
    }
    BackHandler(enabled = contentState?.detailWordKey != null && !isEditorOpen) {
        viewModel.closeDetail()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { WordFlipToastHost(snackbarHostState) },
            topBar = {
                // 编辑器全屏时隐藏学习页 TopBar，避免误触返回「今日」
                if (!isEditorOpen) {
                    WordFlipTopBar(
                        title = groupName.ifBlank { "学习" },
                        onNavigateBack = handleNavigateBack,
                        actions = {
                            WordFlipTopBarAction(
                                icon = Icons.Outlined.Spellcheck,
                                contentDescription = "默写测验",
                                onClick = onNavigateToQuiz,
                            )
                        },
                    )
                }
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
                            onRetry = viewModel::reload,
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
                                    if (autoSpeak) {
                                        viewModel.currentWord(wordKey)?.let { tts.speakForCard(it.en) }
                                    }
                                }
                            },
                            onCardLongClick = viewModel::openDetail,
                        )
                        StudyDetailSheet(
                            word = viewModel.detailWord(),
                            visible = state.detailWordKey != null && !isEditorOpen,
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
                            onToggleStainVisibility = viewModel::toggleStainVisibility,
                            onToggleShowCnOnImage = viewModel::toggleShowCnOnImage,
                            onTakePhoto = { key ->
                                viewModel.markPickTarget(key)
                                pickLaunchers.takePhoto()
                            },
                            onPickGallery = { key ->
                                viewModel.markPickTarget(key)
                                pickLaunchers.pickFromGallery()
                            },
                            onEditPhoto = viewModel::openImageEditor,
                        )
                        val pickSheetKey = state.imagePickSheetWordKey
                        if (pickSheetKey != null) {
                            StudyImagePickSheet(
                                visible = true,
                                onDismiss = viewModel::closeImagePickSheet,
                                onPickGallery = {
                                    viewModel.markPickTarget(pickSheetKey)
                                    pickLaunchers.pickFromGallery()
                                    viewModel.closeImagePickSheet()
                                },
                                onTakePhoto = {
                                    viewModel.markPickTarget(pickSheetKey)
                                    pickLaunchers.takePhoto()
                                    viewModel.closeImagePickSheet()
                                },
                            )
                        }
                        StudyGuideOverlay(
                            visible = state.showGuide,
                            onDismiss = viewModel::dismissGuide,
                        )
                    }
                }
            }
        }

        if (isEditorOpen && editorWord != null) {
            ImageEditorScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f),
                wordEn = editorWord.en,
                cn = editorWord.cn,
                imageUri = editorWord.image.imageUrl.orEmpty(),
                initialTransform = editorWord.image.transform ?: ImageTransform(
                    showCn = editorWord.image.showCnOnImage,
                ),
                initialFilters = editorWord.image.filters ?: ImageFilters(),
                initialShowCn = editorWord.image.showCnOnImage,
                onDismiss = viewModel::closeImageEditor,
                onSave = { transform, filters, showCn ->
                    viewModel.saveImageEdit(editorWord.wordKey, transform, filters, showCn)
                },
                onReplaceImage = {
                    viewModel.requestReplaceImage(editorWord.wordKey)
                },
            )
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
                            wordKey = word.wordKey,
                            stainSeed = word.stain.seed,
                            stainHidden = word.stain.hidden,
                            stainConfig = word.stain.config,
                            hasImage = word.image.hasImage,
                            imageUrl = word.image.imageUrl,
                            imageTransform = word.image.transform,
                            imageFilters = word.image.filters,
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

/** 学习页选图 Sheet（编辑器换图复用） */
@Composable
private fun StudyImagePickSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    WordFlipBottomSheet(visible = visible, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "选择图片",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = onPickGallery, modifier = Modifier.fillMaxWidth()) {
                Text("从相册选择")
            }
            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
                Text("拍照")
            }
        }
    }
}
