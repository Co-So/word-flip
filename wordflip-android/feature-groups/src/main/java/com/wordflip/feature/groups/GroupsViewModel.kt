package com.wordflip.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.network.groups.GroupsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 分组列表 ViewModel（REQ-GROUP-1~5）；GET /groups，已有 Content 时静默刷新（P0-A1D）。
 */
@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupsRepository: GroupsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupsUiState>(GroupsUiState.Loading)
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GroupsUiEvent>()
    val events: SharedFlow<GroupsUiEvent> = _events.asSharedFlow()

    /** 已有内容时静默刷新，避免 Tab 切换闪 Loading */
    fun loadGroups() {
        viewModelScope.launch {
            val current = _uiState.value as? GroupsUiState.Content
            val showLoading = current == null
            if (showLoading) {
                _uiState.value = GroupsUiState.Loading
            }
            groupsRepository.loadGroups()
                .onSuccess { groups ->
                    _uiState.value = GroupsUiState.Content(
                        groups = groups,
                        statusFilter = current?.statusFilter ?: GroupStatusFilter.ALL,
                        sourceFilter = current?.sourceFilter ?: GroupSourceFilter.ALL,
                        searchQuery = current?.searchQuery.orEmpty(),
                    )
                }
                .onFailure { error ->
                    if (_uiState.value is GroupsUiState.Content) {
                        _events.emit(GroupsUiEvent.Toast(error.message ?: "刷新分组失败"))
                    } else {
                        _uiState.value = GroupsUiState.Error(
                            message = error.message ?: "加载分组失败",
                        )
                    }
                }
        }
    }

    fun setStatusFilter(filter: GroupStatusFilter) {
        val content = _uiState.value as? GroupsUiState.Content ?: return
        _uiState.value = content.copy(statusFilter = filter)
    }

    fun setSourceFilter(filter: GroupSourceFilter) {
        val content = _uiState.value as? GroupsUiState.Content ?: return
        _uiState.value = content.copy(sourceFilter = filter)
    }

    fun setSearchQuery(query: String) {
        val content = _uiState.value as? GroupsUiState.Content ?: return
        _uiState.value = content.copy(searchQuery = query)
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
