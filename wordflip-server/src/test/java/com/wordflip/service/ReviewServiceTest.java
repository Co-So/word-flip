package com.wordflip.service;

import com.wordflip.domain.MasteryLevel;
import com.wordflip.domain.Skill;
import com.wordflip.domain.WordSkillProgress;
import com.wordflip.dto.word.MasterySnapshot;
import com.wordflip.repository.StudyLogRepository;
import com.wordflip.repository.WordSkillProgressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * applyQuizResult 状态机 + 稳定性 S 单测（按 skill）。
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Long USER_ID = 1L;
    private static final String WORD_KEY = "apple";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private WordSkillProgressRepository wordSkillProgressRepository;
    @Mock
    private StudyLogRepository studyLogRepository;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void applyQuizResult_correct_incrementsStageAndSetsNextReview() {
        when(wordSkillProgressRepository.findByUserIdAndWordKeyAndSkill(USER_ID, WORD_KEY, Skill.dictation))
                .thenReturn(Optional.empty());

        MasterySnapshot after = reviewService.applyQuizResult(
                USER_ID, WORD_KEY, Skill.dictation, true, false, ZONE);

        assertThat(after.level()).isEqualTo(MasteryLevel.unlearned);
        assertThat(after.hasQuizHistory()).isTrue();
        assertThat(after.stage()).isEqualTo(1);
        assertThat(after.nextReviewAt()).isEqualTo(LocalDate.now(ZONE).plusDays(2));
        assertThat(after.stability()).isGreaterThan(0);
        assertThat(after.heatLevel()).isBetween(0, 4);
        assertThat(after.skill()).isEqualTo(Skill.dictation);

        ArgumentCaptor<WordSkillProgress> captor = ArgumentCaptor.forClass(WordSkillProgress.class);
        verify(wordSkillProgressRepository).save(captor.capture());
        assertThat(captor.getValue().isHasQuizHistory()).isTrue();
        assertThat(captor.getValue().getSkill()).isEqualTo(Skill.dictation);
    }

    @Test
    void applyQuizResult_wrongOnce_setsFuzzyAndStageDown() {
        WordSkillProgress progress = baseProgress(20.0, Skill.dictation);
        progress.setStage(2);
        when(wordSkillProgressRepository.findByUserIdAndWordKeyAndSkill(USER_ID, WORD_KEY, Skill.dictation))
                .thenReturn(Optional.of(progress));

        MasterySnapshot after = reviewService.applyQuizResult(
                USER_ID, WORD_KEY, Skill.dictation, false, false, ZONE);

        assertThat(after.level()).isEqualTo(MasteryLevel.fuzzy);
        assertThat(after.stage()).isEqualTo(1);
        assertThat(after.nextReviewAt()).isEqualTo(LocalDate.now(ZONE).plusDays(1));
        assertThat(after.stability()).isLessThan(20.0);
    }

    @Test
    void applyQuizResult_consecutiveWrong_setsUnknown() {
        when(wordSkillProgressRepository.findByUserIdAndWordKeyAndSkill(USER_ID, WORD_KEY, Skill.choice))
                .thenReturn(Optional.empty());

        MasterySnapshot after = reviewService.applyQuizResult(
                USER_ID, WORD_KEY, Skill.choice, false, true, ZONE);

        assertThat(after.level()).isEqualTo(MasteryLevel.unknown);
        assertThat(after.stage()).isEqualTo(0);
        assertThat(after.nextReviewAt()).isEqualTo(LocalDate.now(ZONE));
        assertThat(after.hasQuizHistory()).isTrue();
        assertThat(after.skill()).isEqualTo(Skill.choice);
    }

    @Test
    void applyQuizResult_sameDayManyCorrect_capsWindowGainAtOne() {
        WordSkillProgress progress = baseProgress(10.0, Skill.dictation);
        progress.setWindowStartedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        progress.setWindowCorrectGain(BigDecimal.ZERO.setScale(2));
        when(wordSkillProgressRepository.findByUserIdAndWordKeyAndSkill(USER_ID, WORD_KEY, Skill.dictation))
                .thenReturn(Optional.of(progress));

        double start = 10.0;
        for (int i = 0; i < 10; i++) {
            MasterySnapshot snap = reviewService.applyQuizResult(
                    USER_ID, WORD_KEY, Skill.dictation, true, false, ZONE);
            assertThat(snap.stability() - start).isLessThanOrEqualTo(1.01);
        }
        assertThat(progress.getWindowCorrectGain().doubleValue()).isLessThanOrEqualTo(1.01);
        assertThat(progress.getStability().doubleValue() - start).isLessThanOrEqualTo(1.01);
    }

    @Test
    void applyQuizResult_longGapCorrect_gainsMoreThanFreshCorrect() {
        WordSkillProgress fresh = baseProgress(40.0, Skill.dictation);
        fresh.setLastQuizAt(Instant.now().minus(2, ChronoUnit.HOURS));
        when(wordSkillProgressRepository.findByUserIdAndWordKeyAndSkill(USER_ID, WORD_KEY, Skill.dictation))
                .thenReturn(Optional.of(fresh));
        double freshGain = reviewService.applyQuizResult(
                USER_ID, WORD_KEY, Skill.dictation, true, false, ZONE).stability() - 40.0;

        WordSkillProgress aged = baseProgress(40.0, Skill.dictation);
        aged.setLastQuizAt(Instant.now().minus(10, ChronoUnit.DAYS));
        when(wordSkillProgressRepository.findByUserIdAndWordKeyAndSkill(USER_ID, WORD_KEY, Skill.dictation))
                .thenReturn(Optional.of(aged));
        double agedGain = reviewService.applyQuizResult(
                USER_ID, WORD_KEY, Skill.dictation, true, false, ZONE).stability() - 40.0;

        assertThat(agedGain).isGreaterThan(freshGain);
    }

    @Test
    void applyQuizResult_shortWindowConsecutiveWrong_dropsStabilityHard() {
        WordSkillProgress progress = baseProgress(50.0, Skill.dictation);
        progress.setWindowStartedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        progress.setLastQuizAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(wordSkillProgressRepository.findByUserIdAndWordKeyAndSkill(USER_ID, WORD_KEY, Skill.dictation))
                .thenReturn(Optional.of(progress));

        double afterFirst = reviewService.applyQuizResult(
                USER_ID, WORD_KEY, Skill.dictation, false, false, ZONE).stability();
        double afterSecond = reviewService.applyQuizResult(
                USER_ID, WORD_KEY, Skill.dictation, false, true, ZONE).stability();

        assertThat(afterFirst).isLessThan(50.0);
        assertThat(afterSecond).isLessThan(afterFirst - 1.0);
    }

    private static WordSkillProgress baseProgress(double stability, Skill skill) {
        WordSkillProgress progress = new WordSkillProgress();
        progress.setUserId(USER_ID);
        progress.setWordKey(WORD_KEY);
        progress.setSkill(skill);
        progress.setHasQuizHistory(true);
        progress.setLevel(MasteryLevel.unlearned);
        progress.setStage(0);
        progress.setStability(BigDecimal.valueOf(stability).setScale(2));
        progress.setWindowCorrectGain(BigDecimal.ZERO.setScale(2));
        progress.setWindowStartedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        progress.setRecentWrongCount(0);
        return progress;
    }
}
