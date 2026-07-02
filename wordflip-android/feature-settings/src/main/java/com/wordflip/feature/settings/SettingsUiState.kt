package com.wordflip.feature.settings

import com.wordflip.core.model.settings.ThemeMode

data class SettingsContent(
    val autoSpeak: Boolean,
    val themeMode: ThemeMode,
)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(val content: SettingsContent) : SettingsUiState
}

sealed interface SettingsUiEvent {
    data class Toast(val message: String) : SettingsUiEvent
    data object Logout : SettingsUiEvent
}
