package com.wordflip.feature.snapshot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.FakeStudyData
import com.wordflip.core.model.fake.MockWordMediaStore
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.model.study.WordCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 卡拍页 ViewModel（REQ-SNAP-1~6）：组内卡片网格、选图/拍照、编辑器保存 Mock。
 */
class SnapshotViewModel(
    private val groupId: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SnapshotUiState>(SnapshotUiState.Loading)
    val uiState: StateFlow<SnapshotUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SnapshotUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SnapshotUiEvent> = _events.asSharedFlow()

    private var baseWords: List<WordCard> = emptyList()

    init {
        loadGroup()
        viewModelScope.launch {
            MockWordMediaStore.revision.collect {
                refreshWords()
            }
        }
    }

    fun reload() = loadGroup()

    private fun loadGroup() {
        viewModelScope.launch {
            _uiState.value = SnapshotUiState.Loading
            delay(150)
            val payload = FakeStudyData.forGroup(groupId)
            if (payload == null) {
                _uiState.value = SnapshotUiState.Error("未找到分组")
                return@launch
            }
            baseWords = payload.words
            _uiState.value = SnapshotUiState.Content(
                groupName = payload.group.name,
                words = mergeWords(),
                flipStates = payload.words.associate { it.wordKey to false },
            )
        }
    }

    private fun mergeWords(): List<WordCard> = MockWordMediaStore.applyToWords(baseWords)

    private fun refreshWords() {
        val content = _uiState.value as? SnapshotUiState.Content ?: return
        _uiState.value = content.copy(words = mergeWords())
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

    /** 编辑器内「换图」：关闭编辑器并弹出选图 Sheet（不可走 onBackFaceClick，否则会立刻重开编辑器） */
    fun requestReplaceImage(wordKey: String) {
        updateContent { it.copy(editorWordKey = null, sheetWordKey = wordKey) }
    }

    /** 记录即将选图的目标词，避免 Activity Result 回调时 sheetWordKey 已丢失 */
    fun markPickTarget(wordKey: String) {
        pickTargetWordKey = wordKey
    }

    /** 相册/相机返回后消费 pick 目标 */
    fun onImagePickedFromLauncher(uri: String) {
        val key = pickTargetWordKey
            ?: (_uiState.value as? SnapshotUiState.Content)?.sheetWordKey
            ?: return
        pickTargetWordKey = null
        onImagePicked(key, uri)
    }

    private var pickTargetWordKey: String? = null

    /** 无图背面点击：打开添加图片 Sheet */
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

    /** P3-A04：选图/拍照后进入编辑器 */
    fun onImagePicked(wordKey: String, uri: String) {
        MockWordMediaStore.saveImage(wordKey, uri)
        openEditor(wordKey)
        viewModelScope.launch {
            _events.emit(SnapshotUiEvent.Toast("已选择图片"))
        }
    }

    /** P3-A06 Mock 保存 */
    fun saveImageEdit(
        wordKey: String,
        transform: ImageTransform,
        filters: ImageFilters,
        showCn: Boolean,
    ) {
        val stored = MockWordMediaStore.getImage(wordKey) ?: return
        MockWordMediaStore.saveImage(
            wordKey = wordKey,
            localUri = stored.localUri,
            transform = transform,
            filters = filters,
            showCnOnImage = showCn,
        )
        closeEditor()
        viewModelScope.launch {
            _events.emit(SnapshotUiEvent.Toast("已保存到卡片"))
        }
    }

    /** P3-A06 Mock 清除 */
    fun clearImage(wordKey: String) {
        MockWordMediaStore.clearImage(wordKey)
        closeSheet()
        closeEditor()
        viewModelScope.launch {
            _events.emit(SnapshotUiEvent.Toast("已清除图片"))
        }
    }

    fun word(wordKey: String): WordCard? {
        return (_uiState.value as? SnapshotUiState.Content)
            ?.words
            ?.firstOrNull { it.wordKey == wordKey }
    }

    class Factory(private val groupId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SnapshotViewModel(groupId) as T
        }
    }
}
