package com.wordflip.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.FakeGroupsData
import com.wordflip.core.model.fake.FakeUnassignedWordsData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 手动添加分组 ViewModel（REQ-CG-1~5）。
 * Mock 拉取未入组词池；保存时调用 FakeGroupsData.createCustomGroup。
 */
class CustomGroupViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<CustomGroupUiState>(CustomGroupUiState.Loading)
    val uiState: StateFlow<CustomGroupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CustomGroupUiEvent>()
    val events: SharedFlow<CustomGroupUiEvent> = _events.asSharedFlow()

    private var selectedKeys: MutableSet<String> = mutableSetOf()

    init {
        loadUnassigned()
    }

    fun loadUnassigned() {
        viewModelScope.launch {
            _uiState.value = CustomGroupUiState.Loading
            delay(200)
            val response = FakeUnassignedWordsData.unassigned()
            selectedKeys = mutableSetOf()
            _uiState.value = CustomGroupUiState.Content(
                words = response.words,
                selectedKeys = selectedKeys.toSet(),
            )
        }
    }

    fun toggleWord(wordKey: String) {
        val content = _uiState.value as? CustomGroupUiState.Content ?: return
        if (wordKey in selectedKeys) {
            selectedKeys.remove(wordKey)
        } else {
            selectedKeys.add(wordKey)
        }
        _uiState.value = content.copy(selectedKeys = selectedKeys.toSet())
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
            delay(150)
            val detail = FakeGroupsData.createCustomGroup(content.selectedKeys.toList())
            _events.emit(CustomGroupUiEvent.Toast("已保存 ${detail.stats.total} 个单词"))
            _events.emit(CustomGroupUiEvent.Saved)
        }
    }
}
