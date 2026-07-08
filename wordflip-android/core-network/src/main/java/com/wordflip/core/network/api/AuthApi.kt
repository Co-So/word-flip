package com.wordflip.core.network.api

import com.wordflip.core.model.auth.AuthResponse
import com.wordflip.core.model.auth.LoginRequest
import com.wordflip.core.model.auth.RegisterRequestBody
import com.wordflip.core.model.auth.TokenRefreshRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Auth REST 契约（openapi Auth 路径）；Refresh 走无 Bearer 的独立 Client 避免拦截器环依赖。
 */
interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequestBody): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body request: TokenRefreshRequest): AuthResponse

    /** 204 No Content；body 可省略 refreshToken 以吊销全部会话 */
    @POST("auth/logout")
    suspend fun logout(@Body request: TokenRefreshRequest?): Response<Unit>
}
