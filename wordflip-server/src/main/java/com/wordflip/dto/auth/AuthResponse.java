package com.wordflip.dto.auth;

import com.wordflip.domain.User;

/**
 * 认证响应：Access + Refresh Token 与用户摘要。
 */
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private AuthUser user;

    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, User user) {
        AuthResponse response = new AuthResponse();
        response.accessToken = accessToken;
        response.refreshToken = refreshToken;
        response.expiresIn = expiresIn;
        response.user = AuthUser.from(user);
        return response;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public AuthUser getUser() {
        return user;
    }

    public record AuthUser(long id, String email, String phone) {
        static AuthUser from(User user) {
            return new AuthUser(user.getId(), user.getEmail(), user.getPhone());
        }
    }
}
