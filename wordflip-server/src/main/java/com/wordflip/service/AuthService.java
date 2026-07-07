package com.wordflip.service;

import com.wordflip.domain.User;
import com.wordflip.domain.UserSettings;
import com.wordflip.domain.UserStatus;
import com.wordflip.dto.auth.AuthResponse;
import com.wordflip.dto.auth.LoginRequest;
import com.wordflip.dto.auth.RegisterRequest;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.UserRepository;
import com.wordflip.repository.UserSettingsRepository;
import com.wordflip.security.JwtService;
import com.wordflip.security.RefreshTokenService;
import com.wordflip.util.AccountUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 认证业务：注册、登录、Refresh 轮换、登出（对齐 openapi Auth 路径）。
 */
@Service
public class AuthService {

    private static final String LOGIN_FAILED_MSG = "账号或密码错误";

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            UserSettingsRepository userSettingsRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * 注册并自动登录；同事务创建 user_settings 默认行（P0-B06）。
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new WordflipException("CONFLICT", "账号已存在");
            }
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new WordflipException("CONFLICT", "账号已存在");
            }
        }

        User user = new User();
        user.setEmail(blankToNull(request.getEmail()));
        user.setPhone(blankToNull(request.getPhone()));
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.active);
        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);

        UserSettings settings = new UserSettings();
        settings.setUserId(user.getId());
        userSettingsRepository.save(settings);

        return issueTokens(user);
    }

    /**
     * 登录：account 自动识别邮箱/手机；失败统一提示，不泄露账号是否存在（REQ-AUTH-6）。
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = findByAccount(request.getAccount())
                .filter(u -> u.getStatus() == UserStatus.active)
                .orElseThrow(() -> new WordflipException("UNAUTHORIZED", LOGIN_FAILED_MSG));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new WordflipException("UNAUTHORIZED", LOGIN_FAILED_MSG);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return issueTokens(user);
    }

    /**
     * Refresh Token 轮换：吊销旧 token，签发新对（openapi /auth/refresh）。
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new WordflipException("VALIDATION_ERROR", "refreshToken 不能为空");
        }
        JwtService.RefreshClaims claims = jwtService.parseRefreshToken(refreshToken)
                .orElseThrow(() -> new WordflipException("UNAUTHORIZED", "Refresh Token 无效或已过期"));

        if (!refreshTokenService.isValid(claims.userId(), claims.tokenId())) {
            throw new WordflipException("UNAUTHORIZED", "Refresh Token 无效或已过期");
        }

        User user = userRepository.findById(claims.userId())
                .filter(u -> u.getStatus() == UserStatus.active)
                .orElseThrow(() -> new WordflipException("UNAUTHORIZED", "Refresh Token 无效或已过期"));

        // 轮换：吊销旧 Refresh
        refreshTokenService.revoke(claims.userId(), claims.tokenId());
        return issueTokens(user);
    }

    /** 登出：提供 refreshToken 时仅吊销该会话；省略时吊销全部 */
    public void logout(Long userId, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            jwtService.parseRefreshToken(refreshToken).ifPresent(claims -> {
                if (claims.userId().equals(userId)) {
                    refreshTokenService.revoke(claims.userId(), claims.tokenId());
                }
            });
        } else {
            refreshTokenService.revokeAll(userId);
        }
    }

    private AuthResponse issueTokens(User user) {
        JwtService.TokenPair pair = jwtService.generateTokenPair(user.getId());
        refreshTokenService.store(user.getId(), pair.refreshTokenId());
        return AuthResponse.of(pair.accessToken(), pair.refreshToken(), pair.expiresInSeconds(), user);
    }

    private java.util.Optional<User> findByAccount(String account) {
        if (AccountUtil.isEmailAccount(account)) {
            return userRepository.findByEmail(account.trim());
        }
        if (AccountUtil.isPhoneAccount(account)) {
            return userRepository.findByPhone(account.trim());
        }
        // 兜底：先邮箱后手机，对外仍统一错误文案
        return userRepository.findByEmail(account.trim())
                .or(() -> userRepository.findByPhone(account.trim()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
