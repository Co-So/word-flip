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
 * 用户偏好设置；分组配置变化只追加未入组卡片，不重排已有成员，也不写学习记忆。
 */
@Service
public class SettingsService {

    private final UserSettingsRepository userSettingsRepository;
    private final GroupService groupService;

    public SettingsService(
            UserSettingsRepository userSettingsRepository,
            GroupService groupService
    ) {
        this.userSettingsRepository = userSettingsRepository;
        this.groupService = groupService;
    }

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        UserSettings settings = requireSettings(userId);
        return UserSettingsResponse.of(settings);
    }

    /**
     * 更新偏好；分组配置实际变化时，在同一事务保存设置并追加当前计划的未入组卡片。
     */
    @Transactional
    public UserSettingsResponse patchPreferences(Long userId, PreferencesPatchRequest request) {
        if (!request.hasAnyField()) {
            throw new WordflipException("VALIDATION_ERROR", "至少提供一个偏好字段");
        }
        UserSettings settings = requireSettings(userId);
        boolean groupingChanged = (request.getGroupSize() != null
                && request.getGroupSize() != settings.getGroupSize())
                || (request.getGroupStrategy() != null
                && request.getGroupStrategy() != settings.getGroupStrategy());
        if (request.getGroupSize() != null) {
            int groupSize = request.getGroupSize();
            if (groupSize != 10 && groupSize != 20 && groupSize != 30 && groupSize != 50) {
                throw new WordflipException("VALIDATION_ERROR", "groupSize 只允许 10、20、30 或 50");
            }
            settings.setGroupSize(groupSize);
        }
        if (request.getGroupStrategy() != null) {
            settings.setGroupStrategy(request.getGroupStrategy());
        }
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
        userSettingsRepository.saveAndFlush(settings);
        if (groupingChanged && settings.getActivePlanId() != null) {
            // 保存成功后再追加，追加异常会让事务整体回滚；已有分组成员保持不变。
            groupService.appendAutoGroups(userId, settings.getActivePlanId());
        }
        return UserSettingsResponse.of(settings);
    }

    private UserSettings requireSettings(Long userId) {
        return userSettingsRepository.findById(userId)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "用户设置不存在"));
    }
}
