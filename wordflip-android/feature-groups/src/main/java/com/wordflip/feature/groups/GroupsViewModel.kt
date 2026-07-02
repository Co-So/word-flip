package com.wordflip.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.FakeGroupsData
import kotlinx.coroutines.delay
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

    init {
        loadGroups()
    }

    fun loadGroups() {
        viewModelScope.launch {
            _uiState.value = GroupsUiState.Loading
            delay(200)
            _uiState.value = GroupsUiState.Content(FakeGroupsData.list())
        }
    }

    fun onSnapshotClick() {
        viewModelScope.launch {
            _events.emit(GroupsUiEvent.Toast("卡拍功能即将上线"))
        }
    }

    fun onStainClick() {
        viewModelScope.launch {
            _events.emit(GroupsUiEvent.Toast("制作污渍功能即将上线"))
        }
    }
}
