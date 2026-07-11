package com.wordflip.dto.settings;

import com.wordflip.domain.HeatDisplayMode;
import com.wordflip.domain.QuizLaunchMode;
import com.wordflip.domain.ThemeMode;

/**
 * 用户偏好 PATCH 请求（不触发分组追加）。
 */
public class PreferencesPatchRequest {

    private Boolean autoSpeak;
    private ThemeMode themeMode;
    private HeatDisplayMode heatDisplayMode;
    private QuizLaunchMode quizLaunchMode;
    private Integer defaultQuestionLimit;
    private String activeDictId;

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

    public HeatDisplayMode getHeatDisplayMode() {
        return heatDisplayMode;
    }

    public void setHeatDisplayMode(HeatDisplayMode heatDisplayMode) {
        this.heatDisplayMode = heatDisplayMode;
    }

    public QuizLaunchMode getQuizLaunchMode() {
        return quizLaunchMode;
    }

    public void setQuizLaunchMode(QuizLaunchMode quizLaunchMode) {
        this.quizLaunchMode = quizLaunchMode;
    }

    public Integer getDefaultQuestionLimit() {
        return defaultQuestionLimit;
    }

    public void setDefaultQuestionLimit(Integer defaultQuestionLimit) {
        this.defaultQuestionLimit = defaultQuestionLimit;
    }

    public String getActiveDictId() {
        return activeDictId;
    }

    public void setActiveDictId(String activeDictId) {
        this.activeDictId = activeDictId;
    }

    public boolean hasAnyField() {
        return autoSpeak != null
                || themeMode != null
                || heatDisplayMode != null
                || quizLaunchMode != null
                || defaultQuestionLimit != null
                || activeDictId != null;
    }
}
