package com.wordflip.feature.study

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.FakeStudyData
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
 * 学习页 ViewModel：Mock 加载、翻转/打乱/全翻状态机（REQ-STUDY-7~13）。
 */
class StudyViewModel(
    private val groupId: Int,
    private val guidePreferences: StudyGuidePreferences,
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

    /** REQ-STUDY-11~12：打乱词序，保留各卡 flipStates */
    fun shuffle() {
        val content = _uiState.value as? StudyUiState.Content ?: return
        if (content.isShuffling) return
        viewModelScope.launch {
            updateContent { it.copy(isShuffling = true) }
            delay(900)
            updateContent { state ->
                state.copy(
                    orderedWords = state.orderedWords.shuffled(),
                    isShuffling = false,
                )
            }
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
            ) as T
        }
    }
}
