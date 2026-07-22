package com.wordflip.core.network.api

import com.wordflip.core.model.book.PreferencesPatchRequest
import com.wordflip.core.model.book.UserSettingsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

/**
 * 用户偏好 API；选书由 learning-plans 负责。
 */
interface SettingsApi {

    @GET("settings")
    suspend fun getSettings(): UserSettingsResponse

    /** 更新偏好（热力展示 / 开测模式 / 默认题数等），不触发分组追加 */
    @PATCH("settings/preferences")
    suspend fun patchPreferences(@Body request: PreferencesPatchRequest): UserSettingsResponse
}
