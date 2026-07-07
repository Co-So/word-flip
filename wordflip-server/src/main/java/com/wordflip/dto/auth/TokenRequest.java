package com.wordflip.dto.auth;

/**
 * Refresh / Logout 请求体（logout 时 refreshToken 可选）。
 */
public class TokenRequest {

    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
