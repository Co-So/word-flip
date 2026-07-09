package com.wordflip.feature.snapshot

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.model.study.WordImagePayload
import com.wordflip.core.network.media.WordMediaRepository
import com.wordflip.core.network.study.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 卡拍页 ViewModel（REQ-SNAP-1~6）：组内卡片网格；媒体走 WordMediaRepository（P3 真 API）。
 */
@HiltViewModel
class SnapshotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val studyRepository: StudyRepository,
    private val wordMediaRepository: WordMediaRepository,
) : ViewModel() {

    private val groupId: Int = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow<SnapshotUiState>(SnapshotUiState.Loading)
    val uiState: StateFlow<SnapshotUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SnapshotUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SnapshotUiEvent> = _events.asSharedFlow()

    /** 选图后、上传前的本地 URI */
    private val pendingLocalUris = mutableMapOf<String, String>()
    private var pickTargetWordKey: String? = null

    init {
        loadGroup()
    }

    fun reload() = loadGroup()

    private fun loadGroup() {
        viewModelScope.launch {
            _uiState.value = SnapshotUiState.Loading
            studyRepository.loadStudyGroup(groupId)
                .onSuccess { payload ->
                    val words = payload.words.map { it.withPendingLocalPreview() }
                    _uiState.value = SnapshotUiState.Content(
                        groupName = payload.group.name,
                        words = words,
                        flipStates = words.associate { it.wordKey to false },
                    )
                }
                .onFailure { error ->
                    _uiState.value = SnapshotUiState.Error(error.message ?: "加载卡拍数据失败")
                }
        }
    }

    private fun WordCard.withPendingLocalPreview(): WordCard {
        val local = pendingLocalUris[wordKey] ?: return this
        return copy(image = image.copy(hasImage = true, imageUrl = local))
    }

    private fun applyImageToWord(wordKey: String, image: WordImagePayload) {
        updateContent { content ->
            content.copy(
                words = content.words.map { card ->
                    if (card.wordKey == wordKey) card.copy(image = image) else card
                },
            )
        }
    }

    private inline fun updateContent(block: (SnapshotUiState.Content) -> SnapshotUiState.Content) {
        val current = _uiState.value
        if (current is SnapshotUiState.Content) {
            _uiState.value = block(current)
        }
    }

    fun toggleFlip(wordKey: String) {
        updateContent { content ->
            val next = !(content.flipStates[wordKey] ?: false)
            content.copy(flipStates = content.flipStates + (wordKey to next))
        }
    }

    fun requestReplaceImage(wordKey: String) {
        updateContent { it.copy(editorWordKey = null, sheetWordKey = wordKey) }
    }

    fun markPickTarget(wordKey: String) {
        pickTargetWordKey = wordKey
    }

    fun onImagePickedFromLauncher(uri: String) {
        val key = pickTargetWordKey
            ?: (_uiState.value as? SnapshotUiState.Content)?.sheetWordKey
            ?: return
        pickTargetWordKey = null
        onImagePicked(key, uri)
    }

    fun onBackFaceClick(wordKey: String) {
        val word = word(wordKey) ?: return
        if (word.image.hasImage) {
            openEditor(wordKey)
        } else {
            updateContent { it.copy(sheetWordKey = wordKey) }
        }
    }

    fun closeSheet() {
        updateContent { it.copy(sheetWordKey = null) }
    }

    fun openEditor(wordKey: String) {
        updateContent { it.copy(sheetWordKey = null, editorWordKey = wordKey) }
    }

    fun closeEditor() {
        updateContent { it.copy(editorWordKey = null) }
    }

    fun onImagePicked(wordKey: String, uri: String) {
        pendingLocalUris[wordKey] = uri
        applyImageToWord(wordKey, WordImagePayload(hasImage = true, imageUrl = uri, showCnOnImage = true))
        openEditor(wordKey)
        viewModelScope.launch {
            _events.emit(SnapshotUiEvent.Toast("已选择图片"))
        }
    }

    /** 有本地待传文件则 POST；否则 PATCH transform */
    fun saveImageEdit(
        wordKey: String,
        transform: ImageTransform,
        filters: ImageFilters,
        showCn: Boolean,
    ) {
        viewModelScope.launch {
            val pendingUri = pendingLocalUris[wordKey]
            val result = if (pendingUri != null) {
                val bytes = readUriBytes(pendingUri)
                if (bytes == null) {
                    _events.emit(SnapshotUiEvent.Toast("读取图片失败"))
                    return@launch
                }
                wordMediaRepository.uploadImage(
                    wordKey = wordKey,
                    fileBytes = bytes,
                    mimeType = guessMime(pendingUri),
                    transform = transform,
                    filters = filters,
                    showCn = showCn,
                )
            } else {
                wordMediaRepository.patchImageTransform(wordKey, transform, filters, showCn)
            }
            result
                .onSuccess { payload ->
                    pendingLocalUris.remove(wordKey)
                    applyImageToWord(wordKey, payload)
                    // 保存后翻到背面便于立即查看（REQ-IMAGE-16）
                    updateContent { content ->
                        content.copy(
                            editorWordKey = null,
                            flipStates = content.flipStates + (wordKey to true),
                        )
                    }
                    _events.emit(SnapshotUiEvent.Toast("已保存到卡片"))
                }
                .onFailure { error ->
                    _events.emit(SnapshotUiEvent.Toast(error.message ?: "保存失败"))
                }
        }
    }

    fun clearImage(wordKey: String) {
        viewModelScope.launch {
            wordMediaRepository.deleteImage(wordKey)
                .onSuccess {
                    pendingLocalUris.remove(wordKey)
                    applyImageToWord(wordKey, WordImagePayload())
                    closeSheet()
                    closeEditor()
                    _events.emit(SnapshotUiEvent.Toast("已清除图片"))
                }
                .onFailure { error ->
                    _events.emit(SnapshotUiEvent.Toast(error.message ?: "清除失败"))
                }
        }
    }

    fun word(wordKey: String): WordCard? {
        return (_uiState.value as? SnapshotUiState.Content)
            ?.words
            ?.firstOrNull { it.wordKey == wordKey }
    }

    private fun readUriBytes(uriString: String): ByteArray? = try {
        appContext.contentResolver.openInputStream(Uri.parse(uriString))?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun guessMime(uriString: String): String {
        val mime = appContext.contentResolver.getType(Uri.parse(uriString))
        return when {
            mime == "image/png" || mime == "image/jpeg" || mime == "image/webp" -> mime
            else -> "image/jpeg"
        }
    }
}
