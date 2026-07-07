package com.wordflip.security;

import com.wordflip.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT 签发与解析：Access 15 分钟；Refresh 含 jti 供 Redis 轮换。
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Access + Refresh Token 对。
     */
    public TokenPair generateTokenPair(Long userId) {
        String tokenId = UUID.randomUUID().toString();
        String accessToken = buildAccessToken(userId);
        String refreshToken = buildRefreshToken(userId, tokenId);
        return new TokenPair(accessToken, refreshToken, tokenId, jwtProperties.getAccessExpirationMinutes() * 60L);
    }

    /** 解析 Access Token，返回 userId */
    public Optional<Long> parseAccessToken(String token) {
        return parseToken(token, TYPE_ACCESS).map(claims -> Long.parseLong(claims.getSubject()));
    }

    /** 解析 Refresh Token，返回 userId 与 tokenId（jti） */
    public Optional<RefreshClaims> parseRefreshToken(String token) {
        return parseToken(token, TYPE_REFRESH).map(claims -> new RefreshClaims(
                Long.parseLong(claims.getSubject()),
                claims.getId()
        ));
    }

    private String buildAccessToken(Long userId) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtProperties.getAccessExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    private String buildRefreshToken(Long userId, String tokenId) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtProperties.getRefreshExpirationDays(), ChronoUnit.DAYS);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(tokenId)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    private Optional<Claims> parseToken(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public record TokenPair(String accessToken, String refreshToken, String refreshTokenId, long expiresInSeconds) {
    }

    public record RefreshClaims(Long userId, String tokenId) {
    }
}
