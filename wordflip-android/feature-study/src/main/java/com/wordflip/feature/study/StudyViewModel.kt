package com.wordflip.feature.study

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.FakeStudyData
import com.wordflip.core.model.study.WordCard
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 学习页 ViewModel：Mock 加载、翻转/打乱/全翻状态机（REQ-STUDY-7~13）。
 */
class StudyViewModel(
    private val groupId: Int,
    private val guidePreferences: StudyGuidePreferences,
    private val appContext: Context,
) : ViewModel() {

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
            delay(200)
            val payload = FakeStudyData.forGroup(groupId)
            if (payload == null) {
                _uiState.value = StudyUiState.Error("未找到分组数据")
                return@launch
            }
            val guideDismissed = guidePreferences.isGuideDismissed()
            _uiState.value = StudyUiState.Content(
                payload = payload,
                orderedWords = payload.words,
                flipStates = payload.words.associate { it.wordKey to false },
                isShuffling = false,
                detailWordKey = null,
                showGuide = !guideDismissed,
                allFlippedToBack = false,
            )
        }
    }

    /**
     * REQ-STUDY-7：点击切换正反面；发音由 StudyScreen 根据设置开关直接触发 TTS。
     */
    fun toggleFlip(wordKey: String) {
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

    /** REQ-STUDY-18 / REQ-STAIN-4：详情抽屉换一个污渍 */
    fun changeStain(wordKey: String) {
        updateContent { content ->
            content.copy(
                orderedWords = content.orderedWords.map { word ->
                    if (word.wordKey != wordKey) word
                    else word.copy(
                        stain = word.stain.copy(
                            seed = Random.nextLong(),
                            hidden = false,
                        ),
                    )
                },
            )
        }
        viewModelScope.launch {
            _events.emit(StudyUiEvent.Toast("已更换污渍"))
            closeDetail()
        }
    }

    /** REQ-STUDY-18 / REQ-STAIN-5：详情抽屉隐藏污渍 */
    fun hideStain(wordKey: String) {
        updateContent { content ->
            content.copy(
                orderedWords = content.orderedWords.map { word ->
                    if (word.wordKey != wordKey) word
                    else word.copy(stain = word.stain.copy(hidden = true))
                },
            )
        }
        viewModelScope.launch {
            _events.emit(StudyUiEvent.Toast("已隐藏污渍"))
            closeDetail()
        }
    }

    /** REQ-STUDY-19：图片上显示中文 overlay 开关（Mock 状态） */
    fun toggleShowCnOnImage(wordKey: String) {
        val word = currentWord(wordKey) ?: return
        if (!word.image.hasImage) {
            viewModelScope.launch {
                _events.emit(StudyUiEvent.Toast("请先添加图片"))
            }
            return
        }
        val nextShow = !word.image.showCnOnImage
        updateContent { content ->
            content.copy(
                orderedWords = content.orderedWords.map { w ->
                    if (w.wordKey != wordKey) w
                    else w.copy(image = w.image.copy(showCnOnImage = nextShow))
                },
            )
        }
        viewModelScope.launch {
            _events.emit(
                StudyUiEvent.Toast(if (nextShow) "已显示中文" else "已隐藏中文"),
            )
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

    class Factory(
        private val context: Context,
        private val groupId: Int,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            return StudyViewModel(
                groupId = groupId,
                guidePreferences = StudyGuidePreferences(appContext),
                appContext = appContext,
            ) as T
        }
    }
}
