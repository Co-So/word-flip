package com.wordflip.feature.groups

import com.wordflip.core.model.study.WordSummary

/** 手动添加分组页 UI 状态（REQ-CG-1~5） */
sealed interface CustomGroupUiState {
    data object Loading : CustomGroupUiState

    data class Content(
        val words: List<WordSummary>,
        val selectedKeys: Set<String>,
    ) : CustomGroupUiState {
        val selectedCount: Int get() = selectedKeys.size
        val isEmpty: Boolean get() = words.isEmpty()
    }

    data class Error(val message: String) : CustomGroupUiState
}

/** 一次性 UI 事件 */
sealed interface CustomGroupUiEvent {
    data class Toast(val message: String) : CustomGroupUiEvent
    data object Saved : CustomGroupUiEvent
}
