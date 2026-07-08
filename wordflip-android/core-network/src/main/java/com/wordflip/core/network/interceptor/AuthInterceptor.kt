package com.wordflip.core.network.interceptor

import com.wordflip.core.network.token.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Bearer Token 注入（A-12）；Auth 白名单路径不附加 Authorization。
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.contains(AUTH_SEGMENT)) {
            return chain.proceed(request)
        }
        val token = tokenStore.getAccessToken() ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header(AUTHORIZATION, "Bearer $token")
                .build(),
        )
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val AUTH_SEGMENT = "/auth/"
    }
}
