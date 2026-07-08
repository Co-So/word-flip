package com.wordflip.core.network.token

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * EncryptedSharedPreferences 实现 TokenStore（REQ-AUTH-5）；密钥由 Android Keystore 保护。
 */
class EncryptedTokenStore(
    context: Context,
) : TokenStore {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _isLoggedIn = MutableStateFlow(readAccessToken() != null)
    override val isLoggedInFlow: Flow<Boolean> = _isLoggedIn.asStateFlow()

    override fun isLoggedIn(): Boolean = readAccessToken() != null

    override fun getAccessToken(): String? = readAccessToken()

    override fun getRefreshToken(): String? =
        prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun getUserId(): Long? {
        val id = prefs.getLong(KEY_USER_ID, NO_USER_ID)
        return id.takeIf { it != NO_USER_ID }
    }

    override fun getExpiresAtEpochMs(): Long? {
        val value = prefs.getLong(KEY_EXPIRES_AT, NO_EXPIRES)
        return value.takeIf { it != NO_EXPIRES }
    }

    override fun saveSession(
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Long,
        userId: Long,
    ) {
        // 预留 30s 缓冲，避免边界时刻 401
        val expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L - 30_000L
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_USER_ID, userId)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
        _isLoggedIn.value = true
    }

    override fun clear() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
    }

    private fun readAccessToken(): String? =
        prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }

    private companion object {
        const val PREFS_NAME = "wordflip_auth_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EXPIRES_AT = "expires_at"
        const val NO_USER_ID = -1L
        const val NO_EXPIRES = -1L
    }
}
