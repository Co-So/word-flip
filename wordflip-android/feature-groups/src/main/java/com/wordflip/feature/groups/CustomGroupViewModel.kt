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
 * 手动添加分组 ViewModel（REQ-CG-1~5）。
 * GET /learning/cards/unassigned?all=true + POST /groups/custom。
 */
@HiltViewModel
class CustomGroupViewModel @Inject constructor(
    private val groupsRepository: GroupsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CustomGroupUiState>(CustomGroupUiState.Loading)
    val uiState: StateFlow<CustomGroupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CustomGroupUiEvent>()
    val events: SharedFlow<CustomGroupUiEvent> = _events.asSharedFlow()

    private var selectedCardIds: MutableSet<Long> = mutableSetOf()

    init {
        loadUnassigned()
    }

    fun loadUnassigned() {
        viewModelScope.launch {
            _uiState.value = CustomGroupUiState.Loading
            groupsRepository.loadUnassignedAll()
                .onSuccess { response ->
                    selectedCardIds = mutableSetOf()
                    _uiState.value = CustomGroupUiState.Content(
                        words = response.words,
                        selectedCardIds = selectedCardIds.toSet(),
                    )
                }
                .onFailure { error ->
                    _uiState.value = CustomGroupUiState.Error(
                        message = error.message ?: "加载未入组单词失败",
                    )
                }
        }
    }

    fun toggleCard(cardId: Long) {
        val content = _uiState.value as? CustomGroupUiState.Content ?: return
        if (cardId in selectedCardIds) {
            selectedCardIds.remove(cardId)
        } else {
            selectedCardIds.add(cardId)
        }
        _uiState.value = content.copy(selectedCardIds = selectedCardIds.toSet())
    }

    /** 保存自定义分组；0 选时 Toast 提示（REQ-CG-4） */
    fun saveCustomGroup() {
        val content = _uiState.value as? CustomGroupUiState.Content ?: return
        if (content.selectedCount == 0) {
            viewModelScope.launch {
                _events.emit(CustomGroupUiEvent.Toast("请先选择单词"))
            }
            return
        }
        viewModelScope.launch {
            groupsRepository.createCustomGroup(content.selectedCardIds.toList())
                .onSuccess { detail ->
                    _events.emit(CustomGroupUiEvent.Toast("已保存 ${detail.stats.total} 个单词"))
                    _events.emit(CustomGroupUiEvent.Saved)
                }
                .onFailure { error ->
                    _events.emit(CustomGroupUiEvent.Toast(error.message ?: "保存分组失败"))
                }
        }
    }
}
