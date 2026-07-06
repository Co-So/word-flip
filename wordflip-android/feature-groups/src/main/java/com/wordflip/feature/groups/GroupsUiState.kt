package com.wordflip.feature.groups

import com.wordflip.core.model.group.GroupDetail

/** 分组列表页 UI 状态 */
sealed interface GroupsUiState {
    data object Loading : GroupsUiState

    data class Content(val groups: List<GroupDetail>) : GroupsUiState

    data class Error(val message: String) : GroupsUiState
}

sealed interface GroupsUiEvent {
    data class Toast(val message: String) : GroupsUiEvent

    data class NavigateToSnapshot(val groupId: Int, val groupName: String) : GroupsUiEvent

    data class NavigateToStainMode(val groupId: Int, val groupName: String) : GroupsUiEvent
}
