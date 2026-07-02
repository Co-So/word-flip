package com.wordflip.feature.stats

import com.wordflip.core.model.stats.StatsDashboard

sealed interface StatsUiState {
    data object Loading : StatsUiState

    data class Content(val dashboard: StatsDashboard) : StatsUiState

    data class Error(val message: String) : StatsUiState
}
