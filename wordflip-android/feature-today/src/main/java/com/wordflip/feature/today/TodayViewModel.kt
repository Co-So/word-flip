package com.wordflip.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.model.today.RecentGroup
import com.wordflip.core.model.today.TodayTask
import com.wordflip.core.network.today.TodayRepository
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
 * 今日页 ViewModel；GET /today，已有 Content 时静默刷新（P0-A1D 模式）。
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val todayRepository: TodayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TodayUiEvent>()
    val events: SharedFlow<TodayUiEvent> = _events.asSharedFlow()

    /** 已有内容时静默刷新，避免 Tab 切换闪 Loading */
    fun loadDashboard() {
        viewModelScope.launch {
            val showLoading = _uiState.value !is TodayUiState.Content
            if (showLoading) {
                _uiState.value = TodayUiState.Loading
            }
            todayRepository.loadDashboard()
                .onSuccess { dashboard ->
                    _uiState.value = TodayUiState.Content(dashboard)
                }
                .onFailure { error ->
                    if (_uiState.value is TodayUiState.Content) {
                        _events.emit(TodayUiEvent.Toast(error.message ?: "刷新今日数据失败"))
                    } else {
                        _uiState.value = TodayUiState.Error(
                            message = error.message ?: "加载今日数据失败",
                        )
                    }
                }
        }
    }

    /** 最近学习组主点击 → 卡片学习（REQ-TODAY-13） */
    fun resolveRecentStudyNavigation(group: RecentGroup): StudyNavigation {
        return StudyNavigation(
            groupId = group.groupId,
            groupName = group.name,
            wordCount = 0,
        )
    }

    /** REQ-TODAY-5：新词/复习任务行 → 取首个来源分组 */
    fun resolveTaskStudyNavigation(task: TodayTask): StudyNavigation? {
        val source = task.sources.firstOrNull() ?: return null
        return StudyNavigation(
            groupId = source.groupId,
            groupName = source.groupName,
            wordCount = source.count,
        )
    }

    /**
     * 任务行副标题（REQ-TODAY-4）：只展示推荐组与组数摘要，避免多组时副标题撑满屏幕。
     */
    fun buildTaskSubtitle(task: TodayTask): String {
        if (task.sources.isEmpty()) {
            return if (task.count == 0) "暂无待办" else "已入组词池"
        }
        val primary = task.sources.first()
        return if (task.sources.size == 1) {
            "${primary.groupName} ${primary.count} 词"
        } else {
            "推荐 ${primary.groupName} ${primary.count} 词 · 共 ${task.sources.size} 组"
        }
    }
}

sealed interface TodayUiEvent {
    data class Toast(val message: String) : TodayUiEvent
}
