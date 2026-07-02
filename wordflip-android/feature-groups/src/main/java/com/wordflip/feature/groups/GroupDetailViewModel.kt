package com.wordflip.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.FakeGroupsData
import com.wordflip.core.model.fake.FakeStudyData
import com.wordflip.core.model.navigation.StudyNavigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 分组详情 ViewModel；掌握度 Chip 只读展示，不写本地态（掌握度仅测验写入）。
 */
class GroupDetailViewModel(
    private val groupId: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.value = GroupDetailUiState.Loading
            delay(200)
            val group = FakeGroupsData.findById(groupId)
            if (group == null) {
                _uiState.value = GroupDetailUiState.Error("未找到分组")
                return@launch
            }
            _uiState.value = GroupDetailUiState.Content(
                group = group,
                words = FakeStudyData.wordsForGroupDetail(groupId),
            )
        }
    }

    fun resolveStudyNavigation(): StudyNavigation? {
        val content = _uiState.value as? GroupDetailUiState.Content ?: return null
        return StudyNavigation(
            groupId = content.group.id,
            groupName = content.group.name,
            wordCount = content.group.stats.total,
        )
    }

    class Factory(private val groupId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GroupDetailViewModel(groupId) as T
        }
    }
}
