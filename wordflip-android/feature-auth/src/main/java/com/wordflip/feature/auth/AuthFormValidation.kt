package com.wordflip.feature.auth

/** 认证表单校验（REQ-AUTH-2~3）；P0-A04 接 API 前本地校验 */
object AuthFormValidation {

    private val emailPattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    private val phonePattern = Regex("""^\+[1-9]\d{1,14}$""")

    enum class RegisterMode { EMAIL, PHONE }

    fun validateLoginAccount(account: String): String? {
        val trimmed = account.trim()
        if (trimmed.isEmpty()) return "请输入账号"
        if (!emailPattern.matches(trimmed) && !phonePattern.matches(trimmed)) {
            return "请输入有效邮箱或手机号"
        }
        return null
    }

    fun validateRegisterEmail(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return "请输入邮箱"
        if (!emailPattern.matches(trimmed)) return "邮箱格式不正确"
        return null
    }

    fun validateRegisterPhone(phone: String): String? {
        val trimmed = phone.trim()
        if (trimmed.isEmpty()) return "请输入手机号"
        if (!phonePattern.matches(trimmed)) return "请使用 E.164 格式，如 +8613800138000"
        return null
    }

    fun validatePassword(password: String, forRegister: Boolean = false): String? {
        if (password.isEmpty()) return "请输入密码"
        if (forRegister && password.length < 8) return "密码至少 8 位"
        return null
    }
}
