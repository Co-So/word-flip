package com.wordflip.feature.snapshot

import com.wordflip.core.model.study.WordCard

sealed interface SnapshotUiState {
    data object Loading : SnapshotUiState

    data class Content(
        val groupName: String,
        val words: List<WordCard>,
        val flipStates: Map<String, Boolean>,
        val sheetWordKey: String? = null,
        val editorWordKey: String? = null,
    ) : SnapshotUiState

    data class Error(val message: String) : SnapshotUiState
}

sealed interface SnapshotUiEvent {
    data class Toast(val message: String) : SnapshotUiEvent
}
