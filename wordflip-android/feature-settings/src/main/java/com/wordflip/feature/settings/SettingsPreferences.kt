package com.wordflip.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wordflip.core.model.settings.HeatDisplayMode
import com.wordflip.core.model.settings.QuizLaunchMode
import com.wordflip.core.model.settings.ThemeMode
import com.wordflip.core.model.settings.parseHeatDisplayMode
import com.wordflip.core.model.settings.parseQuizLaunchMode
import com.wordflip.core.model.settings.parseThemeMode
import com.wordflip.core.model.settings.storageValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings_prefs",
)

private val KEY_AUTO_SPEAK = booleanPreferencesKey("auto_speak")
private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_HEAT_DISPLAY_MODE = stringPreferencesKey("heat_display_mode")
private val KEY_QUIZ_LAUNCH_MODE = stringPreferencesKey("quiz_launch_mode")
private val KEY_DEFAULT_QUESTION_LIMIT = intPreferencesKey("default_question_limit")

/**
 * 用户偏好本地缓存（REQ-SETTINGS-1/2/7）；与 PATCH /settings/preferences 同步。
 */
class SettingsPreferences(
    context: Context,
) {
    private val dataStore = context.settingsDataStore

    val autoSpeakFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        // 对齐 v5 原型：默认开启
        prefs[KEY_AUTO_SPEAK] ?: true
    }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        parseThemeMode(prefs[KEY_THEME_MODE])
    }

    val heatDisplayModeFlow: Flow<HeatDisplayMode> = dataStore.data.map { prefs ->
        parseHeatDisplayMode(prefs[KEY_HEAT_DISPLAY_MODE])
    }

    val quizLaunchModeFlow: Flow<QuizLaunchMode> = dataStore.data.map { prefs ->
        parseQuizLaunchMode(prefs[KEY_QUIZ_LAUNCH_MODE])
    }

    val defaultQuestionLimitFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_DEFAULT_QUESTION_LIMIT] ?: 10).coerceIn(1, 50)
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_SPEAK] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.storageValue()
        }
    }

    suspend fun setHeatDisplayMode(mode: HeatDisplayMode) {
        dataStore.edit { prefs ->
            prefs[KEY_HEAT_DISPLAY_MODE] = mode.storageValue()
        }
    }

    suspend fun setQuizLaunchMode(mode: QuizLaunchMode) {
        dataStore.edit { prefs ->
            prefs[KEY_QUIZ_LAUNCH_MODE] = mode.storageValue()
        }
    }

    suspend fun setDefaultQuestionLimit(limit: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_QUESTION_LIMIT] = limit.coerceIn(1, 50)
        }
    }
}
