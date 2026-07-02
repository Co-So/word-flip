package com.wordflip.feature.study

import com.wordflip.core.model.study.StudyGroupPayload
import com.wordflip.core.model.study.WordCard

/** 学习页 UI 状态 */
sealed interface StudyUiState {
    data object Loading : StudyUiState

    data class Content(
        val payload: StudyGroupPayload,
        val orderedWords: List<WordCard>,
        val flipStates: Map<String, Boolean>,
        val isShuffling: Boolean,
        val detailWordKey: String?,
        val showGuide: Boolean,
        val allFlippedToBack: Boolean,
    ) : StudyUiState

    data class Error(val message: String) : StudyUiState
}

/** 一次性 UI 事件：Toast / TTS 等 */
sealed interface StudyUiEvent {
    data class Toast(val message: String) : StudyUiEvent
}
