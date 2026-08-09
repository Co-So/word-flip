package com.wordflip.controller;

import com.wordflip.dto.settings.PreferencesPatchRequest;
import com.wordflip.dto.settings.UserSettingsResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户设置：分组配置更新可追加未入组卡片，但不重排已有成员，也不写学习记忆。
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public UserSettingsResponse getSettings() {
        return settingsService.getSettings(SecurityUtils.getCurrentUserId());
    }

    @PatchMapping("/preferences")
    public UserSettingsResponse patchPreferences(@Valid @RequestBody PreferencesPatchRequest request) {
        return settingsService.patchPreferences(SecurityUtils.getCurrentUserId(), request);
    }
}
