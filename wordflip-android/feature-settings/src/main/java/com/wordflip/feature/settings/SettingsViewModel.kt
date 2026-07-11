package com.wordflip.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.book.DictionaryItem
import com.wordflip.core.model.book.PreferencesPatchRequest
import com.wordflip.core.model.settings.HeatDisplayMode
import com.wordflip.core.model.settings.QuizLaunchMode
import com.wordflip.core.model.settings.ThemeMode
import com.wordflip.core.model.settings.label
import com.wordflip.core.model.settings.storageValue
import com.wordflip.core.network.api.DictsApi
import com.wordflip.core.network.auth.AuthRepository
import com.wordflip.core.network.settings.PreferencesRepository
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
 * 测验与热力偏好写本地 DataStore，并 PATCH /settings/preferences。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: SettingsPreferences,
    private val preferencesRepository: PreferencesRepository,
    private val dictsApi: DictsApi,
    @ApplicationContext private val appContext: Context,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsUiEvent>()
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    private val dictionariesState = MutableStateFlow<List<DictionaryItem>>(emptyList())

    init {
        viewModelScope.launch {
            runCatching { dictsApi.listDictionaries() }
                .onSuccess { dictionariesState.value = it }
        }
        viewModelScope.launch {
            val baseFlow = combine(
                preferences.autoSpeakFlow,
                preferences.themeModeFlow,
            ) { autoSpeak, themeMode -> autoSpeak to themeMode }
            combine(
                baseFlow,
                preferences.heatDisplayModeFlow,
                preferences.quizLaunchModeFlow,
                preferences.defaultQuestionLimitFlow,
                preferences.activeDictIdFlow,
            ) { base, heatDisplayMode, quizLaunchMode, defaultQuestionLimit, activeDictId ->
                SettingsContent(
                    autoSpeak = base.first,
                    themeMode = base.second,
                    heatDisplayMode = heatDisplayMode,
                    quizLaunchMode = quizLaunchMode,
                    defaultQuestionLimit = defaultQuestionLimit,
                    activeDictId = activeDictId,
                )
            }.combine(dictionariesState) { content, dicts ->
                content.copy(dictionaries = dicts)
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
            patchRemote(PreferencesPatchRequest(autoSpeak = enabled))
            _events.emit(
                SettingsUiEvent.Toast(if (enabled) "已开启自动发音" else "已关闭自动发音"),
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
            patchRemote(PreferencesPatchRequest(themeMode = mode.storageValue()))
            _events.emit(SettingsUiEvent.Toast("外观已切换为${mode.label()}"))
        }
    }

    fun setHeatDisplayMode(mode: HeatDisplayMode) {
        viewModelScope.launch {
            preferences.setHeatDisplayMode(mode)
            patchRemote(PreferencesPatchRequest(heatDisplayMode = mode.storageValue()))
            _events.emit(SettingsUiEvent.Toast("热力展示：${mode.label()}"))
        }
    }

    fun setQuizLaunchMode(mode: QuizLaunchMode) {
        viewModelScope.launch {
            preferences.setQuizLaunchMode(mode)
            patchRemote(PreferencesPatchRequest(quizLaunchMode = mode.storageValue()))
            _events.emit(SettingsUiEvent.Toast("开测模式：${mode.label()}"))
        }
    }

    fun setDefaultQuestionLimit(limit: Int) {
        viewModelScope.launch {
            val coerced = limit.coerceIn(1, 50)
            preferences.setDefaultQuestionLimit(coerced)
            patchRemote(PreferencesPatchRequest(defaultQuestionLimit = coerced))
            _events.emit(SettingsUiEvent.Toast("默认题数：$coerced"))
        }
    }

    fun setActiveDictId(dictId: String, displayName: String) {
        viewModelScope.launch {
            preferences.setActiveDictId(dictId)
            patchRemote(PreferencesPatchRequest(activeDictId = dictId))
            _events.emit(SettingsUiEvent.Toast("词典：$displayName"))
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

    /** 远程同步失败不阻断本地偏好；静默忽略网络错误 */
    private suspend fun patchRemote(request: PreferencesPatchRequest) {
        preferencesRepository.patchPreferences(request)
    }
}
