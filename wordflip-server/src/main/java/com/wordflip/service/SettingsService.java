package com.wordflip.service;

import com.wordflip.domain.UserSettings;
import com.wordflip.dto.settings.PreferencesPatchRequest;
import com.wordflip.dto.settings.UserSettingsResponse;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
/**
 * 用户显示偏好设置；选书和切书统一由学习计划服务负责。
 */
@Service
public class SettingsService {

    private final UserSettingsRepository userSettingsRepository;

    public SettingsService(UserSettingsRepository userSettingsRepository) {
        this.userSettingsRepository = userSettingsRepository;
    }

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        UserSettings settings = requireSettings(userId);
        return UserSettingsResponse.of(settings);
    }

    /** PATCH /settings/preferences：仅更新偏好，不触发 append */
    @Transactional
    public UserSettingsResponse patchPreferences(Long userId, PreferencesPatchRequest request) {
        if (!request.hasAnyField()) {
            throw new WordflipException("VALIDATION_ERROR", "至少提供一个偏好字段");
        }
        UserSettings settings = requireSettings(userId);
        if (request.getAutoSpeak() != null) {
            settings.setAutoSpeak(request.getAutoSpeak());
        }
        if (request.getThemeMode() != null) {
            settings.setThemeMode(request.getThemeMode());
        }
        if (request.getHeatDisplayMode() != null) {
            settings.setHeatDisplayMode(request.getHeatDisplayMode());
        }
        if (request.getQuizLaunchMode() != null) {
            settings.setQuizLaunchMode(request.getQuizLaunchMode());
        }
        if (request.getDefaultQuestionLimit() != null) {
            int limit = request.getDefaultQuestionLimit();
            if (limit < 1 || limit > 50) {
                throw new WordflipException("VALIDATION_ERROR", "defaultQuestionLimit 须在 1–50");
            }
            settings.setDefaultQuestionLimit(limit);
        }
        settings.setUpdatedAt(Instant.now());
        userSettingsRepository.save(settings);
        return UserSettingsResponse.of(settings);
    }

    private UserSettings requireSettings(Long userId) {
        return userSettingsRepository.findById(userId)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "用户设置不存在"));
    }
}
