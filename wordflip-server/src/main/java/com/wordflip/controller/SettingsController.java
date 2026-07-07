package com.wordflip.controller;

import com.wordflip.dto.settings.PreferencesPatchRequest;
import com.wordflip.dto.settings.SaveBooksSettingsRequest;
import com.wordflip.dto.settings.SaveBooksSettingsResponse;
import com.wordflip.dto.settings.UserSettingsResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户设置：GET/PUT /settings、PATCH /settings/preferences（对齐 openapi.yaml Settings）。
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

    @PutMapping
    public SaveBooksSettingsResponse saveSettings(@Valid @RequestBody SaveBooksSettingsRequest request) {
        return settingsService.saveBooksSettings(SecurityUtils.getCurrentUserId(), request);
    }

    @PatchMapping("/preferences")
    public UserSettingsResponse patchPreferences(@Valid @RequestBody PreferencesPatchRequest request) {
        return settingsService.patchPreferences(SecurityUtils.getCurrentUserId(), request);
    }
}
