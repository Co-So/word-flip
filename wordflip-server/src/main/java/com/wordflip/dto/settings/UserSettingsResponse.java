package com.wordflip.dto.settings;

import com.wordflip.domain.DictionaryIds;
import com.wordflip.domain.GroupStrategy;
import com.wordflip.domain.HeatDisplayMode;
import com.wordflip.domain.QuizLaunchMode;
import com.wordflip.domain.ThemeMode;
import com.wordflip.domain.UserSettings;

import java.util.List;

/**
 * 用户设置响应（GET /settings、PATCH /settings/preferences）。
 */
public class UserSettingsResponse {

    private List<Long> bookIds;
    private int groupSize;
    private GroupStrategy groupStrategy;
    private boolean autoSpeak;
    private ThemeMode themeMode;
    private HeatDisplayMode heatDisplayMode;
    private QuizLaunchMode quizLaunchMode;
    private int defaultQuestionLimit;
    private String activeDictId;
    private BooksSummary summary;

    public static UserSettingsResponse of(UserSettings settings, List<Long> bookIds, BooksSummary summary) {
        UserSettingsResponse response = new UserSettingsResponse();
        response.bookIds = bookIds;
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
        response.activeDictId = settings.getActiveDictId() != null && !settings.getActiveDictId().isBlank()
                ? settings.getActiveDictId()
                : DictionaryIds.CURATED;
        response.summary = summary;
        return response;
    }

    public List<Long> getBookIds() {
        return bookIds;
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

    public String getActiveDictId() {
        return activeDictId;
    }

    public BooksSummary getSummary() {
        return summary;
    }
}
