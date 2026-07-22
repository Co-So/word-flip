package com.wordflip.feature.study

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studyViewDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "study_view_prefs",
)

private val KEY_STUDY_VIEW_MODE = stringPreferencesKey("study_view_mode")

/** 学习布局偏好；只保存 UI 模式，不保存业务进度。 */
class StudyViewModePreferences(
    private val context: Context,
) {
    val modeFlow: Flow<StudyViewMode> = context.studyViewDataStore.data.map { preferences ->
        StudyViewMode.fromStorage(preferences[KEY_STUDY_VIEW_MODE])
    }

    /** 持久化学习页展示模式，不改变卡片或学习业务状态。 */
    suspend fun setMode(mode: StudyViewMode) {
        context.studyViewDataStore.edit { preferences ->
            preferences[KEY_STUDY_VIEW_MODE] = mode.storageValue
        }
    }
}
