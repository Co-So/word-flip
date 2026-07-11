package com.wordflip.feature.settings

import com.wordflip.core.model.settings.HeatDisplayMode
import com.wordflip.core.model.settings.QuizLaunchMode
import com.wordflip.core.model.settings.ThemeMode

data class SettingsContent(
    val autoSpeak: Boolean,
    val themeMode: ThemeMode,
    val heatDisplayMode: HeatDisplayMode = HeatDisplayMode.COMBINED,
    val quizLaunchMode: QuizLaunchMode = QuizLaunchMode.MIXED,
    val defaultQuestionLimit: Int = 10,
    val activeDictId: String = "wordflip_curated",
    val dictionaries: List<com.wordflip.core.model.book.DictionaryItem> = emptyList(),
)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(val content: SettingsContent) : SettingsUiState
}

sealed interface SettingsUiEvent {
    data class Toast(val message: String) : SettingsUiEvent
    data object Logout : SettingsUiEvent
}
