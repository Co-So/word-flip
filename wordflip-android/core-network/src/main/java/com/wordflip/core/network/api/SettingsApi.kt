package com.wordflip.core.network.api

import com.wordflip.core.model.book.PreferencesPatchRequest
import com.wordflip.core.model.book.SaveBooksSettingsRequest
import com.wordflip.core.model.book.SaveBooksSettingsResponse
import com.wordflip.core.model.book.UserSettingsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT

/**
 * 用户设置 API（GET/PUT /settings、PATCH /settings/preferences）。
 */
interface SettingsApi {

    @GET("settings")
    suspend fun getSettings(): UserSettingsResponse

    @PUT("settings")
    suspend fun saveSettings(@Body request: SaveBooksSettingsRequest): SaveBooksSettingsResponse

    /** 更新偏好（热力展示 / 开测模式 / 默认题数等），不触发分组追加 */
    @PATCH("settings/preferences")
    suspend fun patchPreferences(@Body request: PreferencesPatchRequest): UserSettingsResponse
}
