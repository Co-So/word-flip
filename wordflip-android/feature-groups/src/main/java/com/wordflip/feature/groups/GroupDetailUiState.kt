package com.wordflip.feature.groups

import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.study.WordCard

sealed interface GroupDetailUiState {
    data object Loading : GroupDetailUiState

    data class Content(
        val group: com.wordflip.core.model.group.GroupDetail,
        val words: List<com.wordflip.core.model.group.GroupWordItem>,
        val stainMode: Boolean = false,
        val stainCards: List<WordCard> = emptyList(),
        val selectedStainTypes: Set<StainType> = emptySet(),
        val stainsHidden: Boolean = false,
    ) : GroupDetailUiState

    data class Error(val message: String) : GroupDetailUiState
}

sealed interface GroupDetailUiEvent {
    data class Toast(val message: String) : GroupDetailUiEvent
}
