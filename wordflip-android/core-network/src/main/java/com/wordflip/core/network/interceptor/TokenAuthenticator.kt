package com.wordflip.core.network.interceptor

import com.wordflip.core.network.auth.TokenRefresher
import com.wordflip.core.network.token.TokenStore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * 401 自动 Refresh 并重试一次（A-14）；并发 401 由 [TokenRefresher] 串行化。
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val tokenRefresher: TokenRefresher,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_RETRY) return null
        if (response.request.url.encodedPath.contains(AUTH_SEGMENT)) return null

        val refreshed = tokenRefresher.refreshSync()
        if (!refreshed) {
            tokenStore.clear()
            return null
        }

        val newToken = tokenStore.getAccessToken() ?: return null
        return response.request.newBuilder()
            .header(AUTHORIZATION, "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val AUTH_SEGMENT = "/auth/"
        const val MAX_RETRY = 2
    }
}
