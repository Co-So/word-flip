package com.wordflip.core.network.api

import com.wordflip.core.model.book.SaveBooksSettingsRequest
import com.wordflip.core.model.book.SaveBooksSettingsResponse
import com.wordflip.core.model.book.UserSettingsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/**
 * 用户设置 API（GET/PUT /settings）。
 */
interface SettingsApi {

    @GET("settings")
    suspend fun getSettings(): UserSettingsResponse

    @PUT("settings")
    suspend fun saveSettings(@Body request: SaveBooksSettingsRequest): SaveBooksSettingsResponse
}
