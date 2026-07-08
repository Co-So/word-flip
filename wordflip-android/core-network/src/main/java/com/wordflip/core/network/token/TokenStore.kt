package com.wordflip.core.network.token

import kotlinx.coroutines.flow.Flow

/**
 * Access/Refresh Token 持久化（A-13）；EncryptedSharedPreferences 实现，供 Auth 拦截器读取。
 */
interface TokenStore {

    /** 是否持有有效 accessToken（本地判定，不含服务端校验） */
    fun isLoggedIn(): Boolean

    /** 登录态变化流，供 NavHost auth gate 订阅 */
    val isLoggedInFlow: Flow<Boolean>

    fun getAccessToken(): String?

    fun getRefreshToken(): String?

    fun getUserId(): Long?

    /** accessToken 过期时间（epoch ms），用于调试；401 时仍尝试 refresh */
    fun getExpiresAtEpochMs(): Long?

    /**
     * 登录/注册/刷新成功后写入会话。
     * @param expiresInSeconds openapi expiresIn（Access Token 秒数）
     */
    fun saveSession(
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Long,
        userId: Long,
    )

    /** 登出或 refresh 失败时清除本地凭证 */
    fun clear()
}
