package com.wordflip.service;

import com.wordflip.domain.GroupWord;
import com.wordflip.domain.StudyGroup;
import com.wordflip.dto.study.StudyGroupPayload;
import com.wordflip.dto.study.StudySessionReportRequest;
import com.wordflip.dto.study.StudySessionReportResponse;
import com.wordflip.dto.study.WordCardDto;
import com.wordflip.dto.study.WordDetailDto;
import com.wordflip.dto.word.MasterySnapshot;
import com.wordflip.dto.word.WordSummary;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.GroupRepository;
import com.wordflip.repository.GroupWordRepository;
import com.wordflip.util.UserTimeZoneUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 学习页聚合与 session 上报（REQ-STUDY-24：翻转不改掌握度）。
 */
@Service
public class StudyService {

    private final GroupRepository groupRepository;
    private final GroupWordRepository groupWordRepository;
    private final WordLookupService wordLookupService;
    private final ReviewService reviewService;
    private final TodayCacheService todayCacheService;

    public StudyService(
            GroupRepository groupRepository,
            GroupWordRepository groupWordRepository,
            WordLookupService wordLookupService,
            ReviewService reviewService,
            TodayCacheService todayCacheService
    ) {
        this.groupRepository = groupRepository;
        this.groupWordRepository = groupWordRepository;
        this.wordLookupService = wordLookupService;
        this.reviewService = reviewService;
        this.todayCacheService = todayCacheService;
    }

    /** GET /study/groups/{groupId}：WordCard 聚合（image/stain P3 占位） */
    @Transactional(readOnly = true)
    public StudyGroupPayload getStudyGroup(Long userId, Long groupId) {
        StudyGroup group = requireOwnedGroup(userId, groupId);
        List<String> wordKeys = groupWordRepository.findByGroupIdOrderBySortOrderAsc(groupId).stream()
                .map(GroupWord::getWordKey)
                .toList();

        Map<String, WordSummary> summaries = wordLookupService.resolveWordSummaries(userId, wordKeys);
        Map<String, MasterySnapshot> masteryMap = reviewService.buildMasterySnapshots(userId, wordKeys);
        Map<String, WordDetailDto> details = wordLookupService.resolveDetails(userId, wordKeys);

        List<WordCardDto> words = new ArrayList<>();
        for (String key : wordKeys) {
            WordSummary summary = summaries.getOrDefault(key, new WordSummary(key, key, "", null, null));
            MasterySnapshot mastery = masteryMap.getOrDefault(key, MasterySnapshot.unlearnedDefault());
            WordDetailDto detail = details.get(key);
            words.add(WordCardDto.from(summary, mastery, detail));
        }

        StudyGroupPayload.StudyGroupInfo info = new StudyGroupPayload.StudyGroupInfo(
                group.getId(),
                group.getName(),
                group.getSource()
        );
        return new StudyGroupPayload(info, words);
    }

    /** POST /study/sessions：upsert study_logs + 失效 Today 缓存 */
    @Transactional
    public StudySessionReportResponse reportSession(
            Long userId,
            StudySessionReportRequest request,
            ZoneId zoneId
    ) {
        requireOwnedGroup(userId, request.getGroupId());
        LocalDate logDate = resolveLogDate(request.getCompletedAt(), zoneId);
        int duration = request.getDurationSec() != null ? request.getDurationSec() : 0;
        int wordsViewed = request.getWordsViewed() != null ? request.getWordsViewed() : 0;

        int streakDays = reviewService.recordStudySession(userId, logDate, duration, wordsViewed);
        todayCacheService.invalidate(userId, logDate);

        return new StudySessionReportResponse(logDate, streakDays);
    }

    private StudyGroup requireOwnedGroup(Long userId, Long groupId) {
        return groupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "分组不存在"));
    }

    private static LocalDate resolveLogDate(Instant completedAt, ZoneId zoneId) {
        if (completedAt != null) {
            return completedAt.atZone(zoneId).toLocalDate();
        }
        return UserTimeZoneUtil.todayInZone(zoneId);
    }
}
