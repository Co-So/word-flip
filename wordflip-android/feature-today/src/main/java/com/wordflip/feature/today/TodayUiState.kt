package com.wordflip.feature.today

import com.wordflip.core.model.today.TodayDashboard

/** 今日页 UI 状态：Loading / Content / Error */
sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Content(val dashboard: TodayDashboard) : TodayUiState

    data class Error(val message: String) : TodayUiState
}

