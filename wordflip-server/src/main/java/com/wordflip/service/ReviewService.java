package com.wordflip.service;

import com.wordflip.domain.MasteryLevel;
import com.wordflip.domain.ReviewPlan;
import com.wordflip.domain.StudyLog;
import com.wordflip.domain.WordMastery;
import com.wordflip.dto.word.MasterySnapshot;
import com.wordflip.repository.ReviewPlanRepository;
import com.wordflip.repository.StudyLogRepository;
import com.wordflip.repository.WordMasteryRepository;
import com.wordflip.util.UserTimeZoneUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 掌握度读路径与 streak 计算（P1）；applyQuizResult 为测验唯一写入口（P2-B05）。
 */
@Service
public class ReviewService {

    /** SRS 间隔（天），索引 0..5 对应 stage（api-modules §2.2） */
    private static final int[] INTERVALS = {1, 2, 4, 7, 15, 30};

    private final WordMasteryRepository wordMasteryRepository;
    private final ReviewPlanRepository reviewPlanRepository;
    private final StudyLogRepository studyLogRepository;

    public ReviewService(
            WordMasteryRepository wordMasteryRepository,
            ReviewPlanRepository reviewPlanRepository,
            StudyLogRepository studyLogRepository
    ) {
        this.wordMasteryRepository = wordMasteryRepository;
        this.reviewPlanRepository = reviewPlanRepository;
        this.studyLogRepository = studyLogRepository;
    }

    /** 单词掌握度快照：合并 word_mastery + review_plans */
    @Transactional(readOnly = true)
    public MasterySnapshot buildMasterySnapshot(Long userId, String wordKey) {
        WordMastery mastery = wordMasteryRepository.findByUserIdAndWordKeyIn(userId, List.of(wordKey))
                .stream()
                .findFirst()
                .orElse(null);
        ReviewPlan plan = reviewPlanRepository.findByUserIdAndWordKey(userId, wordKey).orElse(null);
        if (mastery == null) {
            return plan != null ? MasterySnapshot.withPlan(plan) : MasterySnapshot.unlearnedDefault();
        }
        return MasterySnapshot.from(mastery, plan);
    }

    /** 批量掌握度快照，供 Study 页组装 WordCard */
    @Transactional(readOnly = true)
    public Map<String, MasterySnapshot> buildMasterySnapshots(Long userId, List<String> wordKeys) {
        if (wordKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, WordMastery> masteryByKey = wordMasteryRepository.findByUserIdAndWordKeyIn(userId, wordKeys)
                .stream()
                .collect(Collectors.toMap(WordMastery::getWordKey, m -> m, (a, b) -> a));
        Map<String, ReviewPlan> planByKey = reviewPlanRepository.findByUserIdAndWordKeyIn(userId, wordKeys)
                .stream()
                .collect(Collectors.toMap(ReviewPlan::getWordKey, p -> p, (a, b) -> a));

        Map<String, MasterySnapshot> result = new HashMap<>();
        for (String key : wordKeys) {
            WordMastery mastery = masteryByKey.get(key);
            ReviewPlan plan = planByKey.get(key);
            if (mastery == null) {
                result.put(key, plan != null ? MasterySnapshot.withPlan(plan) : MasterySnapshot.unlearnedDefault());
            } else {
                result.put(key, MasterySnapshot.from(mastery, plan));
            }
        }
        return result;
    }

    /**
     * 连续打卡天数：从 today 向前数活跃 log_date（database-design §10.1）。
     */
    @Transactional(readOnly = true)
    public int calculateStreakDays(Long userId, LocalDate today) {
        List<LocalDate> activeDates = studyLogRepository.findActiveLogDates(userId);
        Set<LocalDate> activeSet = new HashSet<>(activeDates);
        int streak = 0;
        LocalDate cursor = today;
        while (activeSet.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /**
     * upsert study_logs 并返回当日 streak（POST /study/sessions）。
     */
    @Transactional
    public int recordStudySession(
            Long userId,
            LocalDate logDate,
            int durationSec,
            int wordsViewed
    ) {
        StudyLog log = studyLogRepository.findByUserIdAndLogDate(userId, logDate)
                .orElseGet(() -> {
                    StudyLog created = new StudyLog();
                    created.setUserId(userId);
                    created.setLogDate(logDate);
                    return created;
                });
        log.setStudyDurationSec(log.getStudyDurationSec() + Math.max(durationSec, 0));
        log.setWordsViewed(log.getWordsViewed() + Math.max(wordsViewed, 0));
        log.recalculateActivityScore();
        log.setUpdatedAt(Instant.now());
        studyLogRepository.save(log);
        return calculateStreakDays(userId, logDate);
    }

    /**
     * 测验判题后更新 word_mastery + review_plans（掌握度唯一写入口，REQ-QUIZ-6）。
     *
     * @param consecutiveWrong 写入本条答案前该 wordKey 最近一条也为错
     */
    @Transactional
    public MasterySnapshot applyQuizResult(
            Long userId,
            String wordKey,
            boolean correct,
            boolean consecutiveWrong,
            ZoneId zoneId
    ) {
        LocalDate today = UserTimeZoneUtil.todayInZone(zoneId);
        Instant now = Instant.now();

        WordMastery mastery = wordMasteryRepository.findByUserIdAndWordKey(userId, wordKey)
                .orElseGet(() -> {
                    WordMastery created = new WordMastery();
                    created.setUserId(userId);
                    created.setWordKey(wordKey);
                    return created;
                });
        ReviewPlan plan = reviewPlanRepository.findByUserIdAndWordKey(userId, wordKey)
                .orElseGet(() -> {
                    ReviewPlan created = new ReviewPlan();
                    created.setUserId(userId);
                    created.setWordKey(wordKey);
                    return created;
                });

        // hasQuizHistory：首次提交测验答案后恒为 true
        if (!mastery.isHasQuizHistory()) {
            mastery.setHasQuizHistory(true);
            mastery.setFirstQuizAt(now);
        }
        mastery.setUpdatedAt(now);
        plan.setLastQuizAt(now);
        plan.setUpdatedAt(now);

        if (correct) {
            // 答对：level 保持 unlearned（SRS 在档），stage 递增
            mastery.setLevel(MasteryLevel.unlearned);
            int newStage = Math.min(plan.getStage() + 1, 5);
            plan.setStage(newStage);
            plan.setNextReviewAt(today.plusDays(INTERVALS[newStage]));
        } else if (consecutiveWrong) {
            // 跨 session 连续第 2 次答错 → unknown，优先复习队列
            mastery.setLevel(MasteryLevel.unknown);
            plan.setStage(0);
            plan.setNextReviewAt(today);
        } else {
            // 单次答错 → fuzzy，stage 回退
            mastery.setLevel(MasteryLevel.fuzzy);
            plan.setStage(Math.max(plan.getStage() - 1, 0));
            plan.setNextReviewAt(today.plusDays(1));
        }

        wordMasteryRepository.save(mastery);
        reviewPlanRepository.save(plan);
        return MasterySnapshot.from(mastery, plan);
    }
}
