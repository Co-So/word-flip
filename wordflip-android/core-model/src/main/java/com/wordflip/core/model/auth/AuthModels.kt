package com.wordflip.core.model.auth

/**
 * 认证相关 DTO，对齐 openapi Auth 模块；P0-A04 接 Retrofit 时复用。
 */
data class LoginRequest(
    val account: String,
    val password: String,
)

/** 邮箱或手机号二选一注册 */
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
    val expiresIn: Int,
    val user: AuthUser,
)
