package com.wordflip.core.network.settings

import com.wordflip.core.model.book.PreferencesPatchRequest
import com.wordflip.core.model.book.UserSettingsResponse
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.SettingsApi

/**
 * PATCH /settings/preferences 编排；不触发分组增量 append。
 */
class PreferencesRepository(
    private val settingsApi: SettingsApi,
    private val apiErrorParser: ApiErrorParser,
) {

    suspend fun patchPreferences(request: PreferencesPatchRequest): Result<UserSettingsResponse> = try {
        Result.success(settingsApi.patchPreferences(request))
    } catch (throwable: Throwable) {
        Result.failure(Exception(apiErrorParser.parseMessage(throwable), throwable))
    }
}
