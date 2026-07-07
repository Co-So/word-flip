package com.wordflip.dto.settings;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.wordflip.domain.ThemeMode;

import java.util.List;

/**
 * PUT /settings 响应：扁平 JSON（UserSettingsResponse 字段 + appendedGroups）。
 */
public class SaveBooksSettingsResponse {

    @JsonUnwrapped
    private final UserSettingsResponse settings;
    private final AppendedGroups appendedGroups;

    public SaveBooksSettingsResponse(UserSettingsResponse settings, AppendedGroups appendedGroups) {
        this.settings = settings;
        this.appendedGroups = appendedGroups;
    }

    public AppendedGroups getAppendedGroups() {
        return appendedGroups;
    }

    // 供测试读取
    public List<Long> getBookIds() {
        return settings.getBookIds();
    }

    public int getGroupSize() {
        return settings.getGroupSize();
    }

    public boolean isAutoSpeak() {
        return settings.isAutoSpeak();
    }

    public ThemeMode getThemeMode() {
        return settings.getThemeMode();
    }

    public BooksSummary getSummary() {
        return settings.getSummary();
    }
}
