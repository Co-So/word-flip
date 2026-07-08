package com.wordflip.core.model.auth

/**
 * 认证相关 DTO，手写对齐 openapi Auth 模块（A-10）；Retrofit Gson 序列化复用。
 */
data class LoginRequest(
    val account: String,
    val password: String,
)

/**
 * 注册请求 wire 体：email 与 phone 二选一，与后端 RegisterRequest 一致。
 */
data class RegisterRequestBody(
    val email: String? = null,
    val phone: String? = null,
    val password: String,
)

/** ViewModel 层邮箱/手机二选一注册意图 */
sealed class RegisterRequest {
    data class Email(val email: String, val password: String) : RegisterRequest()
    data class Phone(val phone: String, val password: String) : RegisterRequest()
}

data class AuthUser(
    val id: Long,
    val email: String? = null,
    val phone: String? = null,
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: AuthUser,
)

/** POST /auth/refresh 与 logout 可选 refreshToken 体 */
data class TokenRefreshRequest(
    val refreshToken: String,
)
