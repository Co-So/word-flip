package com.wordflip.dto.settings;

import com.wordflip.domain.ThemeMode;
import com.wordflip.domain.UserSettings;

import java.util.List;

/**
 * 用户设置响应（GET /settings、PATCH /settings/preferences）。
 */
public class UserSettingsResponse {

    private List<Long> bookIds;
    private int groupSize;
    private boolean autoSpeak;
    private ThemeMode themeMode;
    private BooksSummary summary;

    public static UserSettingsResponse of(UserSettings settings, List<Long> bookIds, BooksSummary summary) {
        UserSettingsResponse response = new UserSettingsResponse();
        response.bookIds = bookIds;
        response.groupSize = settings.getGroupSize();
        response.autoSpeak = settings.isAutoSpeak();
        response.themeMode = settings.getThemeMode();
        response.summary = summary;
        return response;
    }

    public List<Long> getBookIds() {
        return bookIds;
    }

    public int getGroupSize() {
        return groupSize;
    }

    public boolean isAutoSpeak() {
        return autoSpeak;
    }

    public ThemeMode getThemeMode() {
        return themeMode;
    }

    public BooksSummary getSummary() {
        return summary;
    }
}
