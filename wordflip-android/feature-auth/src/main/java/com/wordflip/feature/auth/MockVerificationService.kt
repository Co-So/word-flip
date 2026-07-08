package com.wordflip.feature.auth

/**
 * 验证码模拟服务（规划项 REQ-AUTH 短信/找回密码；后端上线前本地校验）。
 *
 * 开发模式固定验证码 [DEV_CODE]；发送后在 Toast 提示用户。
 */
object MockVerificationService {

    const val DEV_CODE = "123456"

    /** 验证码用途：注册 / 找回密码 */
    enum class Purpose {
        REGISTER,
        RESET_PASSWORD,
    }

    private data class PendingCode(
        val code: String,
        val expiresAtMs: Long,
    )

    private val pending = mutableMapOf<String, PendingCode>()

    /**
     * 向指定账号（邮箱或 E.164 手机）发送模拟验证码。
     * @return 开发提示文案，供 Toast 展示
     */
    @Synchronized
    fun sendCode(normalizedAccount: String, purpose: Purpose): String {
        val key = cacheKey(normalizedAccount, purpose)
        val code = DEV_CODE
        pending[key] = PendingCode(
            code = code,
            expiresAtMs = System.currentTimeMillis() + CODE_TTL_MS,
        )
        return "验证码已发送（开发模式：$code）"
    }

    /** 校验验证码是否有效 */
    @Synchronized
    fun verify(normalizedAccount: String, purpose: Purpose, code: String): Boolean {
        val key = cacheKey(normalizedAccount, purpose)
        val entry = pending[key] ?: return false
        if (System.currentTimeMillis() > entry.expiresAtMs) {
            pending.remove(key)
            return false
        }
        return entry.code == code.trim()
    }

    private fun cacheKey(account: String, purpose: Purpose): String =
        "${purpose.name}:${account.trim().lowercase()}"

    private const val CODE_TTL_MS = 5 * 60 * 1000L
}
