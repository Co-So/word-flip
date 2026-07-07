package com.wordflip.dto.settings;

import com.wordflip.domain.ThemeMode;

/**
 * 用户偏好 PATCH 请求（不触发分组追加）。
 */
public class PreferencesPatchRequest {

    private Boolean autoSpeak;
    private ThemeMode themeMode;

    public Boolean getAutoSpeak() {
        return autoSpeak;
    }

    public void setAutoSpeak(Boolean autoSpeak) {
        this.autoSpeak = autoSpeak;
    }

    public ThemeMode getThemeMode() {
        return themeMode;
    }

    public void setThemeMode(ThemeMode themeMode) {
        this.themeMode = themeMode;
    }

    public boolean hasAnyField() {
        return autoSpeak != null || themeMode != null;
    }
}
