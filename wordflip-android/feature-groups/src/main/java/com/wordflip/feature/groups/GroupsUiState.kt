package com.wordflip.feature.groups

import com.wordflip.core.model.group.GroupDetail

/** 分组列表页 UI 状态 */
sealed interface GroupsUiState {
    data object Loading : GroupsUiState

    data class Content(val groups: List<GroupDetail>) : GroupsUiState

    data class Error(val message: String) : GroupsUiState
}

/** 分组详情页 UI 状态 */
sealed interface GroupDetailUiState {
    data object Loading : GroupDetailUiState

    data class Content(
        val group: GroupDetail,
        val words: List<com.wordflip.core.model.group.GroupWordItem>,
    ) : GroupDetailUiState

    data class Error(val message: String) : GroupDetailUiState
}

sealed interface GroupsUiEvent {
    data class Toast(val message: String) : GroupsUiEvent
}
