package com.wordflip.feature.auth

/** 认证表单校验（REQ-AUTH-2~3）；注册含确认密码与模拟验证码 gate */
object AuthFormValidation {

    private val emailPattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    private val e164Pattern = Regex("""^\+[1-9]\d{1,14}$""")
    private val cnMobilePattern = Regex("""^1\d{10}$""")

    enum class RegisterMode { EMAIL, PHONE }

    /** 登录账号：邮箱原样；11 位大陆手机自动补 +86 */
    fun normalizeLoginAccount(account: String): String {
        val trimmed = account.trim()
        if (emailPattern.matches(trimmed)) return trimmed
        if (e164Pattern.matches(trimmed)) return trimmed
        val digits = trimmed.filter { it.isDigit() }
        if (cnMobilePattern.matches(digits)) return "+86$digits"
        return trimmed
    }

    fun validateLoginAccount(account: String): String? {
        val trimmed = account.trim()
        if (trimmed.isEmpty()) return "请输入账号"
        val normalized = normalizeLoginAccount(trimmed)
        if (!emailPattern.matches(normalized) && !e164Pattern.matches(normalized)) {
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

    /** 本地号码校验（不含区号） */
    fun validateLocalPhoneNumber(dialCode: String, localNumber: String): String? {
        val digits = localNumber.filter { it.isDigit() }
        if (digits.isEmpty()) return "请输入手机号"
        if (dialCode == "+86") {
            if (!cnMobilePattern.matches(digits)) return "请输入 11 位中国大陆手机号"
        } else if (digits.length < 6) {
            return "手机号格式不正确"
        }
        return null
    }

    /** 拼 E.164 供 API 使用 */
    fun formatPhoneE164(dialCode: String, localNumber: String): String {
        val digits = localNumber.filter { it.isDigit() }
        return dialCode.trim() + digits
    }

    fun validatePassword(password: String, forRegister: Boolean = false): String? {
        if (password.isEmpty()) return "请输入密码"
        if (forRegister && password.length < 8) return "密码至少 8 位"
        return null
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): String? {
        if (confirmPassword.isEmpty()) return "请再次输入密码"
        if (password != confirmPassword) return "两次密码不一致"
        return null
    }

    fun validateVerificationCode(code: String): String? {
        if (code.isBlank()) return "请输入验证码"
        if (code.length != 6 || code.any { !it.isDigit() }) return "验证码为 6 位数字"
        return null
    }
}
