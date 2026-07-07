package com.wordflip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 签发配置：Access 15 分钟、Refresh 7 天（Refresh 存 Redis）。
 */
@Component
@ConfigurationProperties(prefix = "wordflip.jwt")
public class JwtProperties {

    /** HMAC 密钥（生产环境由环境变量注入） */
    private String secret = "dev-only-change-me-wordflip-jwt-secret-key-32bytes!!";

    /** Access Token 有效期（分钟） */
    private int accessExpirationMinutes = 15;

    /** Refresh Token 有效期（天） */
    private int refreshExpirationDays = 7;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getAccessExpirationMinutes() {
        return accessExpirationMinutes;
    }

    public void setAccessExpirationMinutes(int accessExpirationMinutes) {
        this.accessExpirationMinutes = accessExpirationMinutes;
    }

    public int getRefreshExpirationDays() {
        return refreshExpirationDays;
    }

    public void setRefreshExpirationDays(int refreshExpirationDays) {
        this.refreshExpirationDays = refreshExpirationDays;
    }
}
