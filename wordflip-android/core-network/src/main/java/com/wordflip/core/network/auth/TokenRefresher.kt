package com.wordflip.core.network.auth

import com.wordflip.core.model.auth.TokenRefreshRequest
import com.wordflip.core.network.api.AuthApi
import com.wordflip.core.network.token.TokenStore
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException

/**
 * Refresh Token 同步协调器；使用无 Authenticator 的 AuthApi，避免与主 OkHttpClient 环依赖。
 */
class TokenRefresher(
    private val tokenStore: TokenStore,
    private val noAuthAuthApi: AuthApi,
) {
    private val lock = Any()

    /** @return 是否刷新成功并已写入 TokenStore */
    fun refreshSync(): Boolean = synchronized(lock) {
        val refreshToken = tokenStore.getRefreshToken() ?: return false
        return try {
            val response = runBlocking {
                noAuthAuthApi.refresh(TokenRefreshRequest(refreshToken))
            }
            tokenStore.saveSession(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresInSeconds = response.expiresIn,
                userId = response.user.id,
            )
            true
        } catch (exception: HttpException) {
            if (exception.code() == 401) {
                tokenStore.clear()
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}
