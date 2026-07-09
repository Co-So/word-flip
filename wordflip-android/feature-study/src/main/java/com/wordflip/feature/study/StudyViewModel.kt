package com.wordflip.feature.study

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.model.study.WordImagePayload
import com.wordflip.core.model.study.WordStainPayload
import com.wordflip.core.network.media.WordMediaRepository
import com.wordflip.core.network.study.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 学习页 ViewModel：GET /study/groups/{id}；媒体走 WordMediaRepository（P3 真 API）。
 */
@HiltViewModel
class StudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext appContext: Context,
    private val studyRepository: StudyRepository,
    private val wordMediaRepository: WordMediaRepository,
) : ViewModel() {

    private val groupId: Int = checkNotNull(savedStateHandle["groupId"])
    private val guidePreferences = StudyGuidePreferences(appContext)
    private val appContext = appContext

    private val sessionStartedAtMs = System.currentTimeMillis()
    private val viewedWordKeys = mutableSetOf<String>()
    private var sessionReported = false

    /** 选图后、上传前的本地 URI，供编辑器预览 */
    private val pendingLocalUris = mutableMapOf<String, String>()

    private val _uiState = MutableStateFlow<StudyUiState>(StudyUiState.Loading)
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<StudyUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<StudyUiEvent> = _events.asSharedFlow()

    init {
        loadGroup()
    }

    private fun loadGroup() {
        viewModelScope.launch {
            _uiState.value = StudyUiState.Loading
            studyRepository.loadStudyGroup(groupId)
                .onSuccess { payload ->
                    val guideDismissed = guidePreferences.isGuideDismissed()
                    val words = payload.words.map { it.withPendingLocalPreview() }
                    _uiState.value = StudyUiState.Content(
                        payload = payload.copy(words = words),
                        orderedWords = words,
                        flipStates = words.associate { it.wordKey to false },
                        isShuffling = false,
                        detailWordKey = null,
                        showGuide = !guideDismissed,
                        allFlippedToBack = false,
                    )
                }
                .onFailure { error ->
                    _uiState.value = StudyUiState.Error(error.message ?: "加载学习数据失败")
                }
        }
    }

    private fun WordCard.withPendingLocalPreview(): WordCard {
        val local = pendingLocalUris[wordKey] ?: return this
        return copy(image = image.copy(hasImage = true, imageUrl = local))
    }

    private fun applyImageToWord(wordKey: String, image: WordImagePayload) {
        updateContent { state ->
            val mapper: (WordCard) -> WordCard = { card ->
                if (card.wordKey == wordKey) card.copy(image = image) else card
            }
            state.copy(
                orderedWords = state.orderedWords.map(mapper),
                payload = state.payload.copy(words = state.payload.words.map(mapper)),
            )
        }
    }

    private fun applyStainToWord(wordKey: String, stain: WordStainPayload) {
        updateContent { state ->
            val mapper: (WordCard) -> WordCard = { card ->
                if (card.wordKey == wordKey) card.copy(stain = stain) else card
            }
            state.copy(
                orderedWords = state.orderedWords.map(mapper),
                payload = state.payload.copy(words = state.payload.words.map(mapper)),
            )
        }
    }

    fun reload() {
        loadGroup()
    }

    /** 离开学习页时上报 session（REQ-STUDY-24：翻转不改掌握度，仅记 study_logs） */
    fun leaveStudy(onDone: () -> Unit) {
        viewModelScope.launch {
            reportSessionInternal()
            onDone()
        }
    }

    fun reportSessionOnLeave() {
        viewModelScope.launch {
            reportSessionInternal()
        }
    }

    private suspend fun reportSessionInternal() {
        if (sessionReported) return
        sessionReported = true
        val durationSec = ((System.currentTimeMillis() - sessionStartedAtMs) / 1000).toInt()
        studyRepository.reportSession(
            groupId = groupId,
            durationSec = durationSec.coerceAtLeast(0),
            wordsViewed = viewedWordKeys.size,
        )
    }

    /**
     * REQ-STUDY-7：点击切换正反面；发音由 StudyScreen 根据设置开关直接触发 TTS。
     */
    fun toggleFlip(wordKey: String) {
        viewedWordKeys.add(wordKey)
        updateContent { content ->
            if (content.detailWordKey != null) return@updateContent content
            val nextFlipped = !(content.flipStates[wordKey] ?: false)
            val newFlipStates = content.flipStates + (wordKey to nextFlipped)
            content.copy(
                flipStates = newFlipStates,
                allFlippedToBack = newFlipStates.values.all { it },
            )
        }
    }

    /** REQ-STUDY-11~12：扑克牌式收拢→发牌，视觉-数据彻底解耦（列表顺序动画期间保持稳定） */
    fun shuffle(viewportAnchor: ShuffleViewportAnchor) {
        val content = _uiState.value as? StudyUiState.Content ?: return
        if (content.isShuffling) return
        val reduceMotion = ShuffleMotion.isReduceMotionEnabled(appContext)
        val cardCount = content.orderedWords.size
        if (cardCount == 0) return

        viewModelScope.launch {
            val nextEpoch = content.shuffleEpoch + 1
            val motions = content.orderedWords.mapIndexed { index, word ->
                word.wordKey to ShuffleMotion.random(
                    word.wordKey.hashCode().toLong() + index + nextEpoch,
                )
            }.toMap()

            // 1. 收拢阶段：列表保持原顺序，视觉收拢到视口中心
            updateContent {
                it.copy(
                    isShuffling = true,
                    shuffleSettling = false,
                    shuffleEpoch = nextEpoch,
                    shuffleDealStartOffsets = emptyMap(),
                    shuffleVisuals = emptyMap(),
                    shuffleMotions = motions,
                    shuffleViewportAnchor = viewportAnchor,
                    shufflePhase = ShufflePhase.Stacking,
                )
            }
            delay(ShuffleMotion.stackDurationMs(cardCount, reduceMotion))
            delay(ShuffleMotion.STACK_HOLD_MS)

            // 2. 发牌阶段开始：立即重排 orderedWords，同时计算补偿偏移，使视觉位置仍停留在牌堆中心
            val preShuffle = content.orderedWords
            val shuffled = preShuffle.shuffled()
            val dealStartOffsets = shuffled.mapIndexed { newIdx, word ->
                // 新格心（打乱后的布局位置）
                val target = ShuffleMotion.itemCenter(newIdx, ShuffleGridSpec(0f, 0f, 0f), viewportAnchor, word.wordKey)
                // 加入落牌错位，模仿 pukepai.html 的扑克牌扇形/错位感
                val dealOffset = motions[word.wordKey]?.dealOffset ?: (0f to 0f)
                val finalX = target.first + dealOffset.first
                val finalY = target.second + dealOffset.second
                // 补偿偏移 = 视口中心 - 最终落点 → 使卡片在当前布局下仍显示在牌堆中心
                val startTx = viewportAnchor.centerXPx - finalX
                val startTy = viewportAnchor.centerYPx - finalY
                word.wordKey to (startTx to startTy)
            }.toMap()

            updateContent { state ->
                state.copy(
                    orderedWords = shuffled,          // 此刻数据已打乱
                    shufflePhase = ShufflePhase.Dealing,
                    shuffleDealStartOffsets = dealStartOffsets,
                )
            }
            delay(ShuffleMotion.dealDurationMs(cardCount, reduceMotion))

            // 3. 发牌结束，进入 settling 清空偏移（此时列表已是最终顺序，graphicsLayer 归零后无闪动）
            updateContent { state ->
                state.copy(
                    isShuffling = false,
                    shufflePhase = ShufflePhase.None,
                    shuffleSettling = true,
                )
            }
            delay(ShuffleMotion.SETTLE_MS)
            updateContent { state ->
                state.copy(
                    shuffleSettling = false,
                    shuffleDealStartOffsets = emptyMap(),
                    shuffleVisuals = emptyMap(),
                    shuffleMotions = emptyMap(),
                    shuffleViewportAnchor = null,
                )
            }
            _events.emit(StudyUiEvent.Toast("已打乱"))
        }
    }

    /** REQ-STUDY-13：全翻正面或背面 */
    fun flipAll(toBack: Boolean) {
        updateContent { content ->
            content.copy(
                flipStates = content.flipStates.mapValues { toBack },
                allFlippedToBack = toBack,
            )
        }
        viewModelScope.launch {
            _events.emit(
                StudyUiEvent.Toast(if (toBack) "看中文" else "看英文"),
            )
        }
    }

    fun openDetail(wordKey: String) {
        updateContent { it.copy(detailWordKey = wordKey) }
    }

    fun closeDetail() {
        updateContent { it.copy(detailWordKey = null) }
    }

    fun dismissGuide() {
        viewModelScope.launch {
            guidePreferences.dismissGuide()
            updateContent { it.copy(showGuide = false) }
        }
    }

    /** 记录选图目标词（相册/相机 Activity Result 回调时使用） */
    fun markPickTarget(wordKey: String) {
        pickTargetWordKey = wordKey
    }

    fun onImagePickedFromLauncher(uri: String) {
        val state = _uiState.value as? StudyUiState.Content
        val key = pickTargetWordKey
            ?: state?.detailWordKey
            ?: return
        // 从详情抽屉选图后进编辑器，关闭时应回到抽屉
        if (state?.detailWordKey == key) {
            editorReturnDetailWordKey = key
        }
        pickTargetWordKey = null
        pendingLocalUris[key] = uri
        applyImageToWord(key, WordImagePayload(hasImage = true, imageUrl = uri, showCnOnImage = true))
        updateContent { it.copy(editorWordKey = key, detailWordKey = null, imagePickSheetWordKey = null) }
        viewModelScope.launch {
            _events.emit(StudyUiEvent.Toast("已添加图片"))
        }
    }

    /** 从详情抽屉进入编辑器时记录，关闭后恢复抽屉 */
    private var editorReturnDetailWordKey: String? = null

    /** 详情抽屉「编辑照片」 */
    fun openImageEditor(wordKey: String) {
        val state = _uiState.value as? StudyUiState.Content
        editorReturnDetailWordKey =
            if (state?.detailWordKey == wordKey) wordKey else null
        updateContent { it.copy(editorWordKey = wordKey, detailWordKey = null) }
    }

    fun closeImageEditor() {
        val returnDetail = editorReturnDetailWordKey
        editorReturnDetailWordKey = null
        updateContent {
            it.copy(
                editorWordKey = null,
                detailWordKey = returnDetail,
            )
        }
    }

    /** 编辑器内换图：关闭编辑器并弹出选图 Sheet */
    fun requestReplaceImage(wordKey: String) {
        markPickTarget(wordKey)
        updateContent { it.copy(editorWordKey = null, imagePickSheetWordKey = wordKey) }
    }

    fun closeImagePickSheet() {
        updateContent { it.copy(imagePickSheetWordKey = null) }
    }

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
                    _events.emit(StudyUiEvent.Toast("读取图片失败"))
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
                    closeImageEditor()
                    _events.emit(StudyUiEvent.Toast("已保存到卡片"))
                }
                .onFailure { error ->
                    _events.emit(StudyUiEvent.Toast(error.message ?: "保存失败"))
                }
        }
    }

    private var pickTargetWordKey: String? = null

    fun editorWord(): WordCard? {
        val key = (_uiState.value as? StudyUiState.Content)?.editorWordKey ?: return null
        return currentWord(key)
    }

    /** REQ-STUDY-18 / REQ-STAIN-4：换一个污渍；保持抽屉打开以便连续预览 */
    fun changeStain(
        wordKey: String,
        allowedTypes: List<StainType> = StainType.entries,
    ) {
        viewModelScope.launch {
            wordMediaRepository.regenerateStain(wordKey, allowedTypes.ifEmpty { StainType.entries })
                .onSuccess { applyStainToWord(wordKey, it) }
                .onFailure { _events.emit(StudyUiEvent.Toast(it.message ?: "更换污渍失败")) }
        }
    }

    /** REQ-STUDY-18 / REQ-STAIN-5~7：显示/隐藏污渍切换 */
    fun toggleStainVisibility(wordKey: String) {
        val word = currentWord(wordKey) ?: return
        viewModelScope.launch {
            wordMediaRepository.setStainHidden(wordKey, !word.stain.hidden)
                .onSuccess { applyStainToWord(wordKey, it) }
                .onFailure { _events.emit(StudyUiEvent.Toast(it.message ?: "更新污渍失败")) }
        }
    }

    /** REQ-STUDY-19：图片上显示中文 overlay 开关 */
    fun toggleShowCnOnImage(wordKey: String) {
        val word = currentWord(wordKey) ?: return
        if (!word.image.hasImage) {
            viewModelScope.launch {
                _events.emit(StudyUiEvent.Toast("请先添加图片"))
            }
            return
        }
        val nextShow = !word.image.showCnOnImage
        val transform = word.image.transform ?: ImageTransform()
        val filters = word.image.filters ?: ImageFilters()
        viewModelScope.launch {
            wordMediaRepository.patchImageTransform(wordKey, transform, filters, nextShow)
                .onSuccess {
                    applyImageToWord(wordKey, it)
                    _events.emit(StudyUiEvent.Toast(if (nextShow) "已显示中文" else "已隐藏中文"))
                }
                .onFailure { _events.emit(StudyUiEvent.Toast(it.message ?: "更新失败")) }
        }
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

    fun currentWord(wordKey: String): WordCard? {
        return (_uiState.value as? StudyUiState.Content)
            ?.orderedWords
            ?.firstOrNull { it.wordKey == wordKey }
    }

    fun detailWord(): WordCard? {
        val key = (_uiState.value as? StudyUiState.Content)?.detailWordKey ?: return null
        return currentWord(key)
    }

    private inline fun updateContent(block: (StudyUiState.Content) -> StudyUiState.Content) {
        val current = _uiState.value
        if (current is StudyUiState.Content) {
            _uiState.value = block(current)
        }
    }
}
