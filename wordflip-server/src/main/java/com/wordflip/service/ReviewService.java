package com.wordflip.service;

import com.wordflip.domain.HeatDisplayMode;
import com.wordflip.domain.MasteryLevel;
import com.wordflip.domain.Skill;
import com.wordflip.domain.StudyLog;
import com.wordflip.domain.WordSkillProgress;
import com.wordflip.dto.word.MasterySnapshot;
import com.wordflip.dto.word.WordProgressSnapshot;
import com.wordflip.repository.StudyLogRepository;
import com.wordflip.repository.WordSkillProgressRepository;
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
 * 按 skill 的掌握度读写；applyQuizResult 为测验唯一写入口。
 */
@Service
public class ReviewService {

    private static final int[] INTERVALS = {1, 2, 4, 7, 15, 30};

    private final WordSkillProgressRepository wordSkillProgressRepository;
    private final StudyLogRepository studyLogRepository;

    public ReviewService(
            WordSkillProgressRepository wordSkillProgressRepository,
            StudyLogRepository studyLogRepository
    ) {
        this.wordSkillProgressRepository = wordSkillProgressRepository;
        this.studyLogRepository = studyLogRepository;
    }

    @Transactional(readOnly = true)
    public MasterySnapshot buildMasterySnapshot(Long userId, String wordKey, Skill skill) {
        return wordSkillProgressRepository.findByUserIdAndWordKeyAndSkill(userId, wordKey, skill)
                .map(MasterySnapshot::from)
                .orElseGet(() -> MasterySnapshot.unlearnedDefault(skill));
    }

    /** 兼容旧调用：默认 dictation */
    @Transactional(readOnly = true)
    public MasterySnapshot buildMasterySnapshot(Long userId, String wordKey) {
        return buildMasterySnapshot(userId, wordKey, Skill.dictation);
    }

    @Transactional(readOnly = true)
    public WordProgressSnapshot buildWordProgress(Long userId, String wordKey, HeatDisplayMode mode) {
        MasterySnapshot d = buildMasterySnapshot(userId, wordKey, Skill.dictation);
        MasterySnapshot c = buildMasterySnapshot(userId, wordKey, Skill.choice);
        return WordProgressSnapshot.of(d, c, mode);
    }

    /** 批量双 skill 进度，供 Study / Groups */
    @Transactional(readOnly = true)
    public Map<String, WordProgressSnapshot> buildWordProgressMap(
            Long userId,
            List<String> wordKeys,
            HeatDisplayMode mode
    ) {
        if (wordKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<Skill, WordSkillProgress>> byKey = new HashMap<>();
        for (WordSkillProgress p : wordSkillProgressRepository.findByUserIdAndWordKeyIn(userId, wordKeys)) {
            byKey.computeIfAbsent(p.getWordKey(), k -> new HashMap<>()).put(p.getSkill(), p);
        }
        Map<String, WordProgressSnapshot> result = new HashMap<>();
        for (String key : wordKeys) {
            Map<Skill, WordSkillProgress> skills = byKey.getOrDefault(key, Map.of());
            MasterySnapshot d = skills.containsKey(Skill.dictation)
                    ? MasterySnapshot.from(skills.get(Skill.dictation))
                    : MasterySnapshot.unlearnedDefault(Skill.dictation);
            MasterySnapshot c = skills.containsKey(Skill.choice)
                    ? MasterySnapshot.from(skills.get(Skill.choice))
                    : MasterySnapshot.unlearnedDefault(Skill.choice);
            result.put(key, WordProgressSnapshot.of(d, c, mode));
        }
        return result;
    }

    /** Study 页仍要 Map&lt;wordKey, MasterySnapshot&gt;：用展示热力对应的 dictation 为主快照兼容 */
    @Transactional(readOnly = true)
    public Map<String, MasterySnapshot> buildMasterySnapshots(Long userId, List<String> wordKeys) {
        Map<String, WordProgressSnapshot> progress = buildWordProgressMap(userId, wordKeys, HeatDisplayMode.combined);
        return progress.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().dictation()));
    }

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

    @Transactional
    public int recordStudySession(Long userId, LocalDate logDate, int durationSec, int wordsViewed) {
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
     * 测验判题后更新指定 skill 的进度（三态/SRS + 稳定性 S）。
     */
    @Transactional
    public MasterySnapshot applyQuizResult(
            Long userId,
            String wordKey,
            Skill skill,
            boolean correct,
            boolean consecutiveWrong,
            ZoneId zoneId
    ) {
        LocalDate today = UserTimeZoneUtil.todayInZone(zoneId);
        Instant now = Instant.now();
        Skill resolved = skill != null ? skill : Skill.dictation;

        WordSkillProgress progress = wordSkillProgressRepository
                .findByUserIdAndWordKeyAndSkill(userId, wordKey, resolved)
                .orElseGet(() -> {
                    WordSkillProgress created = new WordSkillProgress();
                    created.setUserId(userId);
                    created.setWordKey(wordKey);
                    created.setSkill(resolved);
                    return created;
                });

        Instant previousQuizAt = progress.getLastQuizAt();
        applyStability(progress, previousQuizAt, correct, now);

        if (!progress.isHasQuizHistory()) {
            progress.setHasQuizHistory(true);
            progress.setFirstQuizAt(now);
        }
        progress.setLastQuizAt(now);
        progress.setUpdatedAt(now);

        if (correct) {
            progress.setLevel(MasteryLevel.unlearned);
            int newStage = Math.min(progress.getStage() + 1, 5);
            progress.setStage(newStage);
            progress.setNextReviewAt(today.plusDays(INTERVALS[newStage]));
        } else if (consecutiveWrong) {
            progress.setLevel(MasteryLevel.unknown);
            progress.setStage(0);
            progress.setNextReviewAt(today);
        } else {
            progress.setLevel(MasteryLevel.fuzzy);
            progress.setStage(Math.max(progress.getStage() - 1, 0));
            progress.setNextReviewAt(today.plusDays(1));
        }

        wordSkillProgressRepository.save(progress);
        return MasterySnapshot.from(progress);
    }

    /** 兼容旧签名：默认 dictation */
    @Transactional
    public MasterySnapshot applyQuizResult(
            Long userId,
            String wordKey,
            boolean correct,
            boolean consecutiveWrong,
            ZoneId zoneId
    ) {
        return applyQuizResult(userId, wordKey, Skill.dictation, correct, consecutiveWrong, zoneId);
    }

    private void applyStability(WordSkillProgress progress, Instant previousQuizAt, boolean correct, Instant now) {
        if (StabilityCalculator.isWindowExpired(progress.getWindowStartedAt(), now)) {
            progress.setWindowStartedAt(now);
            progress.setWindowCorrectGain(StabilityCalculator.toStored(0));
            progress.setRecentWrongCount(0);
        }

        double s = StabilityCalculator.fromStored(progress.getStability());
        double gap = StabilityCalculator.gapDays(previousQuizAt, now);
        double windowGain = StabilityCalculator.fromStored(progress.getWindowCorrectGain());

        if (correct) {
            double delta = StabilityCalculator.correctDelta(s, gap, windowGain);
            s = StabilityCalculator.applyDelta(s, delta);
            progress.setStability(StabilityCalculator.toStored(s));
            progress.setWindowCorrectGain(StabilityCalculator.toStored(windowGain + delta));
            progress.setRecentWrongCount(0);
        } else {
            int wrongAfter = progress.getRecentWrongCount() + 1;
            progress.setRecentWrongCount(wrongAfter);
            double deltaMinus = StabilityCalculator.wrongDelta(s, gap, wrongAfter);
            s = StabilityCalculator.applyDelta(s, -deltaMinus);
            progress.setStability(StabilityCalculator.toStored(s));
        }
    }
}
