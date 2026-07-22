package com.wordflip.feature.snapshot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.image.ImageEditorScreen
import com.wordflip.core.image.rememberImagePickerLaunchers
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.ui.apple.AppleContextActionRow
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.ApplePageTitle
import com.wordflip.core.ui.apple.ApplePrimaryAction
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.component.FlipCard
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipBottomSheet
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.rememberWordFlipToast

/**
 * 卡拍页（REQ-SNAP-1~6、P3-A03）：用图片记忆工作台组织组内卡片与上下文操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotScreen(
    groupId: Int,
    groupName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SnapshotViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()
    val content = uiState as? SnapshotUiState.Content
    val sheetWordKey = content?.sheetWordKey

    val pickLaunchers = rememberImagePickerLaunchers(
        onImagePicked = viewModel::onImagePickedFromLauncher,
        onCameraDenied = { toast.show("需要相机权限才能拍照") },
    )

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SnapshotUiEvent.Toast -> toast.show(event.message)
            }
        }
    }

    val editorWord = content?.editorWordKey?.let { viewModel.word(it) }
    val isEditorOpen = editorWord != null && editorWord.image.hasImage
    val isSheetOpen = content?.sheetWordKey != null

    val handleNavigateBack: () -> Unit = {
        when {
            isEditorOpen -> viewModel.closeEditor()
            isSheetOpen -> viewModel.closeSheet()
            else -> onNavigateBack()
        }
    }

    BackHandler(enabled = isEditorOpen) {
        viewModel.closeEditor()
    }
    BackHandler(enabled = isSheetOpen && !isEditorOpen) {
        viewModel.closeSheet()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = AppleUi.colors.canvas,
            topBar = {
                if (!isEditorOpen) {
                    WordFlipTopBar(
                        title = groupName.ifBlank { "卡拍" },
                        onNavigateBack = handleNavigateBack,
                    )
                }
            },
        ) { innerPadding ->
            when (val state = uiState) {
                SnapshotUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is SnapshotUiState.Error -> {
                    NetworkErrorView(
                        message = state.message,
                        onRetry = viewModel::reload,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is SnapshotUiState.Content -> {
                    SnapshotWorkbench(
                        state = state,
                        modifier = Modifier.padding(innerPadding),
                        onFlip = viewModel::toggleFlip,
                        onBackFaceClick = viewModel::onBackFaceClick,
                        onOpenEditor = viewModel::openEditor,
                        onOpenActions = viewModel::requestReplaceImage,
                    )
                }
            }
        }

        if (editorWord != null && editorWord.image.hasImage) {
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
                onDismiss = viewModel::closeEditor,
                onSave = { transform, filters, showCn ->
                    viewModel.saveImageEdit(editorWord.wordKey, transform, filters, showCn)
                },
                onReplaceImage = {
                    viewModel.requestReplaceImage(editorWord.wordKey)
                },
            )
        }

        // Toast 叠在编辑器之上，保存失败时用户才能看到提示。
        WordFlipToastHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f),
        )
    }

    val sheetWord = sheetWordKey?.let { viewModel.word(it) }
    SnapshotActionSheet(
        word = sheetWord,
        visible = sheetWord != null && !isEditorOpen,
        onDismiss = viewModel::closeSheet,
        onPickGallery = {
            sheetWord?.wordKey?.let(viewModel::markPickTarget)
            pickLaunchers.pickFromGallery()
        },
        onTakePhoto = {
            sheetWord?.wordKey?.let(viewModel::markPickTarget)
            pickLaunchers.takePhoto()
        },
        onClear = viewModel::clearImage,
        onEdit = viewModel::openEditor,
    )
}

/** 工作台抬头与卡片网格只重排展示，不计算或写入学习进度。 */
@Composable
private fun SnapshotWorkbench(
    state: SnapshotUiState.Content,
    onFlip: (String) -> Unit,
    onBackFaceClick: (String) -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenActions: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageCount = state.words.count { it.image.hasImage }
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        ApplePageTitle(
            title = "图片记忆",
            subtitle = "已为 $imageCount/${state.words.size} 张卡片添加图片",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text = "翻到背面查看记忆图片；没有图片时，可以从生活场景开始建立联想。",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = AppleUi.colors.secondaryText,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 164.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.words, key = { it.wordKey }) { word ->
                val flipped = state.flipStates[word.wordKey] == true
                SnapshotWorkbenchCard(
                    word = word,
                    flipped = flipped,
                    onFlip = { onFlip(word.wordKey) },
                    onBackFaceClick = { onBackFaceClick(word.wordKey) },
                    onOpenEditor = { onOpenEditor(word.wordKey) },
                    onOpenActions = { onOpenActions(word.wordKey) },
                )
            }
        }
    }
}

/** 单卡同时保留翻面/长按语义，并补充可发现的图片主操作。 */
@Composable
private fun SnapshotWorkbenchCard(
    word: WordCard,
    flipped: Boolean,
    onFlip: () -> Unit,
    onBackFaceClick: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenActions: () -> Unit,
) {
    val colors = AppleUi.colors
    AppleGroupedSurface(
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = word.en,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (word.image.hasImage) {
                    colors.accent.copy(alpha = 0.14f)
                } else {
                    colors.elevatedSurface
                },
            ) {
                Text(
                    text = if (word.image.hasImage) "已有图片" else "待添加",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (word.image.hasImage) colors.accent else colors.secondaryText,
                )
            }
        }

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
            onClick = if (flipped) onBackFaceClick else onFlip,
            onLongClick = {
                if (word.image.hasImage) onOpenEditor()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )

        Text(
            text = word.cn?.takeIf { it.isNotBlank() } ?: "暂无中文释义",
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (word.image.hasImage) {
            AppleContextActionRow(
                label = if (flipped) "编辑图片" else "查看图片",
                supportingText = if (flipped) "调整构图、滤镜与中文条" else "翻到图片记忆面",
                onClick = if (flipped) onOpenEditor else onFlip,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(
                onClick = onOpenActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
            ) {
                Text("更多图片操作")
            }
        } else {
            Text(
                text = "添加熟悉的场景，让词义更容易被唤起。",
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText,
            )
            ApplePrimaryAction(
                text = "添加图片",
                onClick = onBackFaceClick,
            )
        }
    }
}

/** 图片相关动作集中在操作表中，清除项使用明确的危险语义。 */
@Composable
private fun SnapshotActionSheet(
    word: WordCard?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onClear: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    WordFlipBottomSheet(visible = visible, onDismiss = onDismiss) {
        word ?: return@WordFlipBottomSheet
        val colors = AppleUi.colors
        var confirmClear by remember(word.wordKey) { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (word.image.hasImage) "「${word.en}」的图片" else "为「${word.en}」添加图片",
                style = MaterialTheme.typography.titleLarge,
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (word.image.hasImage) {
                    "编辑当前记忆图片，或从相机和相册更换。"
                } else {
                    "选择一个与你对这个词的记忆有关的场景。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText,
            )

            if (word.image.hasImage) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.groupedSurface,
                ) {
                    Column {
                        AppleContextActionRow(
                            label = "编辑图片",
                            supportingText = "调整旋转、缩放与滤镜",
                            onClick = { onEdit(word.wordKey) },
                        )
                        HorizontalDivider(color = colors.separator)
                        AppleContextActionRow(
                            label = "中文显示",
                            supportingText = "在编辑器中设置图片上的中文条",
                            onClick = { onEdit(word.wordKey) },
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.groupedSurface,
            ) {
                Column {
                    AppleContextActionRow(
                        label = if (word.image.hasImage) "拍照更换" else "拍照",
                        supportingText = "用相机记录当前场景",
                        onClick = onTakePhoto,
                    )
                    HorizontalDivider(color = colors.separator)
                    AppleContextActionRow(
                        label = if (word.image.hasImage) "从相册更换" else "从相册选择",
                        supportingText = "使用设备中的现有图片",
                        onClick = onPickGallery,
                    )
                }
            }

            if (word.image.hasImage) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.groupedSurface,
                ) {
                    AppleContextActionRow(
                        label = "清除图片",
                        supportingText = "会从这张卡片移除，且无法撤销",
                        destructive = true,
                        onClick = { confirmClear = true },
                    )
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
            ) {
                Text("取消")
            }
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("清除这张图片？") },
                text = { Text("图片会从「${word.en}」卡片中移除，且无法撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmClear = false
                            onClear(word.wordKey)
                        },
                    ) {
                        Text(
                            text = "清除图片",
                            color = colors.destructive,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) {
                        Text("保留图片")
                    }
                },
            )
        }
    }
}
