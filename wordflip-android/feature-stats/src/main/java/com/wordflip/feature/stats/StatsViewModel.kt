package com.wordflip.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.FakeStatsData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 统计页 ViewModel；Mock 加载 FakeStatsData（REQ-STATS-1~3） */
class StatsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = StatsUiState.Loading
            delay(200)
            _uiState.value = StatsUiState.Content(FakeStatsData.dashboard)
        }
    }

    fun quizAccuracyPercent(accuracy: Float): Int = (accuracy * 100).roundToInt()
}
