package com.wordflip.core.network.auth

import com.wordflip.core.model.auth.AuthResponse
import com.wordflip.core.model.auth.LoginRequest
import com.wordflip.core.model.auth.RegisterRequest
import com.wordflip.core.model.auth.RegisterRequestBody
import com.wordflip.core.model.auth.TokenRefreshRequest
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.AuthApi
import com.wordflip.core.network.token.TokenStore

/**
 * 认证业务编排（P0-A04）：register/login/logout/refresh，成功后写入 TokenStore。
 */
class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val apiErrorParser: ApiErrorParser,
) {

    suspend fun login(account: String, password: String): Result<Unit> = apiCall {
        val response = authApi.login(
            LoginRequest(account = account.trim(), password = password),
        )
        persistAuthResponse(response)
    }

    suspend fun register(request: RegisterRequest): Result<Unit> = apiCall {
        val body = when (request) {
            is RegisterRequest.Email -> RegisterRequestBody(
                email = request.email.trim(),
                password = request.password,
            )
            is RegisterRequest.Phone -> RegisterRequestBody(
                phone = request.phone.trim(),
                password = request.password,
            )
        }
        val response = authApi.register(body)
        persistAuthResponse(response)
    }

    /** REQ-AUTH-5：调用 logout API 后清除本地 Token */
    suspend fun logout() {
        val refreshToken = tokenStore.getRefreshToken()
        try {
            authApi.logout(refreshToken?.let { TokenRefreshRequest(it) })
        } catch (_: Exception) {
            // 网络失败仍清本地，避免用户无法退出
        } finally {
            tokenStore.clear()
        }
    }

    private fun persistAuthResponse(response: AuthResponse) {
        tokenStore.saveSession(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresInSeconds = response.expiresIn,
            userId = response.user.id,
        )
    }

    private suspend fun apiCall(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (throwable: Throwable) {
        Result.failure(Exception(apiErrorParser.parseMessage(throwable), throwable))
    }
}
