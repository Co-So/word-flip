package com.wordflip.dto.settings;

import com.wordflip.domain.GroupStrategy;
import com.wordflip.domain.HeatDisplayMode;
import com.wordflip.domain.QuizLaunchMode;
import com.wordflip.domain.ThemeMode;
import com.wordflip.domain.UserSettings;

/**
 * 用户设置响应（GET /settings、PATCH /settings/preferences）。
 */
public class UserSettingsResponse {

    private Long activePlanId;
    private int groupSize;
    private GroupStrategy groupStrategy;
    private boolean autoSpeak;
    private ThemeMode themeMode;
    private HeatDisplayMode heatDisplayMode;
    private QuizLaunchMode quizLaunchMode;
    private int defaultQuestionLimit;
    public static UserSettingsResponse of(UserSettings settings) {
        UserSettingsResponse response = new UserSettingsResponse();
        response.activePlanId = settings.getActivePlanId();
        response.groupSize = settings.getGroupSize();
        response.groupStrategy = settings.getGroupStrategy() != null
                ? settings.getGroupStrategy()
                : GroupStrategy.book_order;
        response.autoSpeak = settings.isAutoSpeak();
        response.themeMode = settings.getThemeMode();
        response.heatDisplayMode = settings.getHeatDisplayMode() != null
                ? settings.getHeatDisplayMode()
                : HeatDisplayMode.combined;
        response.quizLaunchMode = settings.getQuizLaunchMode() != null
                ? settings.getQuizLaunchMode()
                : QuizLaunchMode.mixed;
        response.defaultQuestionLimit = settings.getDefaultQuestionLimit() > 0
                ? settings.getDefaultQuestionLimit()
                : 10;
        return response;
    }

    public Long getActivePlanId() {
        return activePlanId;
    }

    public int getGroupSize() {
        return groupSize;
    }

    public GroupStrategy getGroupStrategy() {
        return groupStrategy;
    }

    public boolean isAutoSpeak() {
        return autoSpeak;
    }

    public ThemeMode getThemeMode() {
        return themeMode;
    }

    public HeatDisplayMode getHeatDisplayMode() {
        return heatDisplayMode;
    }

    public QuizLaunchMode getQuizLaunchMode() {
        return quizLaunchMode;
    }

    public int getDefaultQuestionLimit() {
        return defaultQuestionLimit;
    }

}
