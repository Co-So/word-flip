package com.wordflip.feature.study

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.studyGuideDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "study_guide_prefs",
)

private val KEY_GUIDE_DISMISSED = booleanPreferencesKey("study_long_press_guide_dismissed")

/** REQ-STUDY-22~23：首次引导持久化 */
class StudyGuidePreferences(
    private val context: Context,
) {
    suspend fun isGuideDismissed(): Boolean {
        return context.studyGuideDataStore.data.first()[KEY_GUIDE_DISMISSED] == true
    }

    suspend fun dismissGuide() {
        context.studyGuideDataStore.edit { prefs ->
            prefs[KEY_GUIDE_DISMISSED] = true
        }
    }
}
