package com.wordflip.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.FakeGroupsData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 分组列表 ViewModel（REQ-GROUP-1~5） */
class GroupsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<GroupsUiState>(GroupsUiState.Loading)
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GroupsUiEvent>()
    val events: SharedFlow<GroupsUiEvent> = _events.asSharedFlow()

    /** 已有内容时静默刷新，避免 Tab 切换闪 Loading */
    fun loadGroups() {
        viewModelScope.launch {
            val showLoading = _uiState.value !is GroupsUiState.Content
            if (showLoading) {
                _uiState.value = GroupsUiState.Loading
            }
            _uiState.value = GroupsUiState.Content(FakeGroupsData.list())
        }
    }

    fun onSnapshotClick(groupId: Int, groupName: String) {
        viewModelScope.launch {
            _events.emit(GroupsUiEvent.NavigateToSnapshot(groupId, groupName))
        }
    }

    fun onStainClick(groupId: Int, groupName: String) {
        viewModelScope.launch {
            _events.emit(GroupsUiEvent.NavigateToStainMode(groupId, groupName))
        }
    }
}
