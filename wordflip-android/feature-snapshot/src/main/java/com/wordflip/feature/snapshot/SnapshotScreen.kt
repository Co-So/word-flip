package com.wordflip.feature.snapshot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordflip.core.image.ImageEditorScreen
import com.wordflip.core.image.rememberImagePickerLaunchers
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.ui.component.FlipCard
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipBottomSheet
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.rememberWordFlipToast

/**
 * 卡拍页（REQ-SNAP-1~6、P3-A03）：组内两列卡片网格，无图背面可点出 Sheet。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotScreen(
    groupId: Int,
    groupName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SnapshotViewModel = viewModel(factory = SnapshotViewModel.Factory(groupId)),
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
            snackbarHost = { WordFlipToastHost(snackbarHostState) },
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        Text(
                            text = "点击卡片翻面，背面点一下即可拍照或选图；已添加图片后点背面进入编辑",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.words, key = { it.wordKey }) { word ->
                                val flipped = state.flipStates[word.wordKey] == true
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
                                    onClick = {
                                        if (flipped) {
                                            viewModel.onBackFaceClick(word.wordKey)
                                        } else {
                                            viewModel.toggleFlip(word.wordKey)
                                        }
                                    },
                                    onLongClick = {
                                        if (word.image.hasImage) {
                                            viewModel.openEditor(word.wordKey)
                                        }
                                    },
                                )
                            }
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
                onDismiss = viewModel::closeEditor,
                onSave = { transform, filters, showCn ->
                    viewModel.saveImageEdit(editorWord.wordKey, transform, filters, showCn)
                },
                onReplaceImage = {
                    viewModel.requestReplaceImage(editorWord.wordKey)
                },
            )
        }
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "为「${word.en}」添加图片",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = onPickGallery,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("从相册选择") }
            OutlinedButton(
                onClick = onTakePhoto,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("拍照") }
            if (word.image.hasImage) {
                OutlinedButton(
                    onClick = { onEdit(word.wordKey) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("编辑图片") }
                OutlinedButton(
                    onClick = { onClear(word.wordKey) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("清除图片") }
            }
        }
    }
}
