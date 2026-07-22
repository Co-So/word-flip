package com.wordflip.feature.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.zIndex
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.image.ImageEditorScreen
import com.wordflip.core.image.rememberImagePickerLaunchers
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.component.WordFlipBottomSheet
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.WordFlipTopBarAction
import com.wordflip.core.ui.component.rememberWordFlipToast
import com.wordflip.feature.settings.SettingsPreferences
import kotlinx.coroutines.launch

/**
 * 卡片学习页（REQ-STUDY-1~3）：在三种布局中共用同一学习状态。
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
    val viewModePreferences = remember(context) {
        StudyViewModePreferences(context.applicationContext)
    }
    val viewMode by viewModePreferences.modeFlow.collectAsState(initial = StudyViewMode.HYBRID)
    val scope = rememberCoroutineScope()
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
            topBar = {
                // 编辑器全屏时隐藏学习页 TopBar，避免误触返回「今日」
                if (!isEditorOpen) {
                    WordFlipTopBar(
                        title = groupName.ifBlank { "学习" },
                        onNavigateBack = handleNavigateBack,
                        actions = {
                            StudyModePicker(
                                mode = viewMode,
                                onModeSelected = { selected ->
                                    scope.launch { viewModePreferences.setMode(selected) }
                                },
                            )
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
                        // 空分组先短路，避免 pager 访问不存在的页面索引。
                        if (state.orderedWords.isEmpty()) {
                            StudyEmptyState(
                                title = "这个分组还没有学习卡",
                                message = "返回分组后添加已发布学习卡再继续。",
                                onNavigateBack = handleNavigateBack,
                            )
                        } else {
                            StudyModeContent(
                                mode = viewMode,
                                state = state,
                                reduceMotion = reduceMotion,
                                onShuffle = viewModel::shuffle,
                                onFlipAll = {
                                    viewModel.flipAll(toBack = !state.allFlippedToBack)
                                },
                                onCardClick = { wordKey ->
                                    // 点击事件读取 StateFlow 实时值，避免连续点击复用 Compose 捕获快照
                                    val currentState = viewModel.uiState.value as? StudyUiState.Content
                                    if (currentState != null && currentState.detailWordKey == null) {
                                        val result = reduceStudyFlip(
                                            wasFlipped = currentState.flipStates[wordKey] == true,
                                            autoSpeakEnabled = autoSpeak,
                                        )
                                        viewModel.toggleFlip(wordKey)
                                        if (result.shouldAutoSpeak) {
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

        // Toast 叠在编辑器之上，保存失败时用户才能看到提示
        WordFlipToastHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f),
        )
    }
}

/** 学习分组无可用卡片时的明确退路。 */
@Composable
private fun StudyEmptyState(
    title: String,
    message: String,
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = AppleUi.colors.primaryText,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = AppleUi.colors.secondaryText,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onNavigateBack,
            modifier = Modifier
                .padding(top = 20.dp)
                .heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppleUi.colors.accent),
        ) {
            Text("返回分组")
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
