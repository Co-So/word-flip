package com.wordflip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.domain.GroupWord;
import com.wordflip.domain.StudyGroup;
import com.wordflip.domain.WordImage;
import com.wordflip.domain.WordStain;
import com.wordflip.dto.media.ImageTransform;
import com.wordflip.dto.media.WordImagePayload;
import com.wordflip.dto.stain.StainConfig;
import com.wordflip.dto.stain.WordStainPayload;
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
import com.wordflip.repository.WordImageRepository;
import com.wordflip.repository.WordStainRepository;
import com.wordflip.util.StableHash;
import com.wordflip.util.UserTimeZoneUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final WordImageRepository wordImageRepository;
    private final WordStainRepository wordStainRepository;
    private final ObjectMapper objectMapper;

    public StudyService(
            GroupRepository groupRepository,
            GroupWordRepository groupWordRepository,
            WordLookupService wordLookupService,
            ReviewService reviewService,
            TodayCacheService todayCacheService,
            WordImageRepository wordImageRepository,
            WordStainRepository wordStainRepository,
            ObjectMapper objectMapper
    ) {
        this.groupRepository = groupRepository;
        this.groupWordRepository = groupWordRepository;
        this.wordLookupService = wordLookupService;
        this.reviewService = reviewService;
        this.todayCacheService = todayCacheService;
        this.wordImageRepository = wordImageRepository;
        this.wordStainRepository = wordStainRepository;
        this.objectMapper = objectMapper;
    }

    /** GET /study/groups/{groupId}：WordCard 聚合（含 image/stain，P3） */
    @Transactional(readOnly = true)
    public StudyGroupPayload getStudyGroup(Long userId, Long groupId) {
        StudyGroup group = requireOwnedGroup(userId, groupId);
        List<String> wordKeys = groupWordRepository.findByGroupIdOrderBySortOrderAsc(groupId).stream()
                .map(GroupWord::getWordKey)
                .toList();

        Map<String, WordSummary> summaries = wordLookupService.resolveWordSummaries(userId, wordKeys);
        Map<String, MasterySnapshot> masteryMap = reviewService.buildMasterySnapshots(userId, wordKeys);
        Map<String, WordDetailDto> details = wordLookupService.resolveDetails(userId, wordKeys);
        Map<String, WordImage> imageMap = wordKeys.isEmpty()
                ? Map.of()
                : wordImageRepository.findByUserIdAndWordKeyIn(userId, wordKeys).stream()
                .collect(Collectors.toMap(WordImage::getWordKey, Function.identity(), (a, b) -> a));
        Map<String, WordStain> stainMap = wordKeys.isEmpty()
                ? Map.of()
                : wordStainRepository.findByUserIdAndWordKeyIn(userId, wordKeys).stream()
                .collect(Collectors.toMap(WordStain::getWordKey, Function.identity(), (a, b) -> a));

        List<WordCardDto> words = new ArrayList<>();
        for (String key : wordKeys) {
            WordSummary summary = summaries.getOrDefault(key, new WordSummary(key, key, "", null, null));
            MasterySnapshot mastery = masteryMap.getOrDefault(key, MasterySnapshot.unlearnedDefault());
            WordDetailDto detail = details.get(key);
            // 有图则签发 presigned URL；无图 hasImage=false
            WordImagePayload image = toImagePayload(imageMap.get(key));
            // 无污渍行：默认 seed，不落库（与 StainService 一致）
            WordStainPayload stain = toStainPayload(userId, key, stainMap.get(key));
            words.add(WordCardDto.from(summary, mastery, detail, image, stain));
        }

        StudyGroupPayload.StudyGroupInfo info = new StudyGroupPayload.StudyGroupInfo(
                group.getId(),
                group.getName(),
                group.getSource()
        );
        return new StudyGroupPayload(info, words);
    }

    private WordImagePayload toImagePayload(WordImage image) {
        if (image == null) {
            return WordImagePayload.none();
        }
        // 与 ImageService 一致：返回后端代理路径，避免客户端直连 MinIO
        String url = ImageService.mediaProxyUrl(image.getUserId(), image.getWordKey());
        return new WordImagePayload(true, url, readTransform(image.getTransformJson()));
    }

    private WordStainPayload toStainPayload(Long userId, String wordKey, WordStain row) {
        if (row == null) {
            return new WordStainPayload(false, new StainConfig(StableHash.defaultStainSeed(userId, wordKey)));
        }
        return new WordStainPayload(row.isHidden(), readStainConfig(row, userId, wordKey));
    }

    private ImageTransform readTransform(String json) {
        try {
            return objectMapper.readValue(json, ImageTransform.class).withDefaults();
        } catch (JsonProcessingException e) {
            throw new WordflipException("INTERNAL_ERROR", "读取 transform 失败");
        }
    }

    private StainConfig readStainConfig(WordStain row, Long userId, String wordKey) {
        if (row.getStainConfigJson() == null || row.getStainConfigJson().isBlank()) {
            return new StainConfig(StableHash.defaultStainSeed(userId, wordKey));
        }
        try {
            return objectMapper.readValue(row.getStainConfigJson(), StainConfig.class);
        } catch (JsonProcessingException e) {
            throw new WordflipException("INTERNAL_ERROR", "污渍配置解析失败");
        }
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
