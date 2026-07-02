package com.wordflip.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.FakeTodayData
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.model.today.RecommendedStudy
import com.wordflip.core.model.today.TodayTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 今日页 ViewModel；MVP 阶段从 FakeTodayData 加载，后续替换为 TodayRepository。
 */
class TodayViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = TodayUiState.Loading
            // 模拟网络延迟，便于预览 Loading 态
            delay(300)
            _uiState.value = TodayUiState.Content(FakeTodayData.dashboard)
        }
    }

    /** REQ-TODAY-7：底部 CTA 目标分组 */
    fun resolveStudyNavigation(recommended: RecommendedStudy?): StudyNavigation? {
        recommended ?: return null
        return StudyNavigation(
            groupId = recommended.groupId,
            groupName = recommended.groupName,
            wordCount = recommended.wordCount,
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

    /** 组装 CTA 文案：「开始学习 · 第3组 · 20 词」 */
    fun buildStartStudyLabel(recommended: RecommendedStudy?): String {
        recommended ?: return "开始学习"
        return "开始学习 · ${recommended.groupName} · ${recommended.wordCount} 词"
    }

    /** 任务行副标题：分组来源与数量 */
    fun buildTaskSubtitle(task: TodayTask): String {
        if (task.sources.isEmpty()) {
            return "已入组词池"
        }
        return task.sources.joinToString(" · ") { "${it.groupName} ${it.count} 词" }
    }
}
