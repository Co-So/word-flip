package com.wordflip.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.settings.ThemeMode
import com.wordflip.core.model.settings.label
import com.wordflip.core.network.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 ViewModel（REQ-SETTINGS-1~7、P0-A06）；退出登录调 AuthRepository 清 Token。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: SettingsPreferences,
    @ApplicationContext private val appContext: Context,
    private val authRepository: AuthRepository,
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
            if (enabled && !checkTtsAvailable(appContext)) {
                _events.emit(
                    SettingsUiEvent.Toast(
                        "未检测到可用的文字转语音，请在系统设置中安装或启用",
                    ),
                )
            }
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

    /** REQ-AUTH-5：POST /auth/logout + 清本地 Token，NavHost 回登录页 */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _events.emit(SettingsUiEvent.Logout)
        }
    }
}
