package com.wordflip.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.settings.ThemeMode
import com.wordflip.core.model.settings.label
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel（REQ-SETTINGS-1~7）；Toggle/主题写入 DataStore，规划项 Toast 占位。
 */
class SettingsViewModel(
    private val preferences: SettingsPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsUiEvent>()
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.autoSpeakFlow,
                preferences.themeModeFlow,
            ) { autoSpeak, themeMode ->
                SettingsContent(autoSpeak = autoSpeak, themeMode = themeMode)
            }.collect { content ->
                _uiState.value = SettingsUiState.Content(content)
            }
        }
    }

    fun toggleAutoSpeak(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoSpeak(enabled)
            _events.emit(
                SettingsUiEvent.Toast(if (enabled) "已开启自动发音" else "已关闭自动发音"),
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
            _events.emit(SettingsUiEvent.Toast("外观已切换为${mode.label()}"))
        }
    }

    fun onPlaceholderClick(label: String) {
        viewModelScope.launch {
            _events.emit(SettingsUiEvent.Toast("$label 功能即将上线"))
        }
    }

    fun logout() {
        viewModelScope.launch {
            _events.emit(SettingsUiEvent.Logout)
        }
    }

    class Factory(
        private val preferences: SettingsPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferences) as T
        }
    }
}
