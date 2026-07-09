package com.wordflip.service;

import com.wordflip.domain.GroupStrategy;
import com.wordflip.domain.UserBookSelection;
import com.wordflip.domain.UserSettings;
import com.wordflip.dto.settings.AppendedGroups;
import com.wordflip.dto.settings.PreferencesPatchRequest;
import com.wordflip.dto.settings.SaveBooksSettingsRequest;
import com.wordflip.dto.settings.SaveBooksSettingsResponse;
import com.wordflip.dto.settings.UserSettingsResponse;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.UserBookSelectionRepository;
import com.wordflip.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 用户设置：词书勾选、分组大小、偏好；PUT /settings 触发增量 append。
 */
@Service
public class SettingsService {

    private final UserSettingsRepository userSettingsRepository;
    private final UserBookSelectionRepository userBookSelectionRepository;
    private final BookService bookService;
    private final GroupService groupService;
    private final TodayCacheService todayCacheService;

    public SettingsService(
            UserSettingsRepository userSettingsRepository,
            UserBookSelectionRepository userBookSelectionRepository,
            BookService bookService,
            GroupService groupService,
            TodayCacheService todayCacheService
    ) {
        this.userSettingsRepository = userSettingsRepository;
        this.userBookSelectionRepository = userBookSelectionRepository;
        this.bookService = bookService;
        this.groupService = groupService;
        this.todayCacheService = todayCacheService;
    }

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        UserSettings settings = requireSettings(userId);
        List<Long> bookIds = userBookSelectionRepository.findBookIdsByUserId(userId);
        var summary = bookService.buildSummary(userId, settings.getGroupSize());
        return UserSettingsResponse.of(settings, bookIds, summary);
    }

    /**
     * 保存词书勾选与分组大小，并调用 appendGroupsForNewWords（REQ-BOOK-17 增量追加）。
     */
    @Transactional
    public SaveBooksSettingsResponse saveBooksSettings(Long userId, SaveBooksSettingsRequest request) {
        if (!request.isGroupSizeValid()) {
            throw new WordflipException("VALIDATION_ERROR", "groupSize 须为 10、20、30 或 50");
        }

        List<Long> bookIds = request.getBookIds() == null ? List.of() : request.getBookIds();
        if (Boolean.TRUE.equals(request.getRegroup()) && bookIds.isEmpty()) {
            throw new WordflipException("VALIDATION_ERROR", "重新分组需至少勾选一本词书");
        }
        bookService.validateBookSelection(userId, bookIds);

        UserSettings settings = requireSettings(userId);
        settings.setGroupSize(request.getGroupSize());
        if (request.getGroupStrategy() != null) {
            settings.setGroupStrategy(request.getGroupStrategy());
        }
        settings.setUpdatedAt(Instant.now());
        userSettingsRepository.save(settings);

        // 全量替换勾选列表；selected_at 递增以保留 PUT body 中的词书顺序（REQ-BOOK-23）
        userBookSelectionRepository.deleteAllByUserId(userId);
        Instant selectionBase = Instant.now();
        for (int i = 0; i < bookIds.size(); i++) {
            UserBookSelection selection = new UserBookSelection(userId, bookIds.get(i));
            selection.setSelectedAt(selectionBase.plusMillis(i));
            userBookSelectionRepository.save(selection);
        }

        AppendedGroups appended = Boolean.TRUE.equals(request.getRegroup())
                ? groupService.regroupAutoGroups(userId)
                : groupService.appendGroupsForNewWords(userId);
        // regroup 会重建 auto 组（groupId 变化），须清 Today 缓存避免 stale recommendedStudy
        todayCacheService.invalidateAllForUser(userId);
        UserSettingsResponse response = getSettings(userId);
        return new SaveBooksSettingsResponse(response, appended);
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
        return getSettings(userId);
    }

    private UserSettings requireSettings(Long userId) {
        return userSettingsRepository.findById(userId)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "用户设置不存在"));
    }
}
