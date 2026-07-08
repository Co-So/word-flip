package com.wordflip.service;

import com.wordflip.domain.MasteryLevel;
import com.wordflip.domain.ReviewPlan;
import com.wordflip.domain.WordMastery;
import com.wordflip.dto.word.MasterySnapshot;
import com.wordflip.repository.ReviewPlanRepository;
import com.wordflip.repository.StudyLogRepository;
import com.wordflip.repository.WordMasteryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * applyQuizResult 状态机单测（Q-01 / P2-B05）。
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Long USER_ID = 1L;
    private static final String WORD_KEY = "apple";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private WordMasteryRepository wordMasteryRepository;
    @Mock
    private ReviewPlanRepository reviewPlanRepository;
    @Mock
    private StudyLogRepository studyLogRepository;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void applyQuizResult_correct_incrementsStageAndSetsNextReview() {
        when(wordMasteryRepository.findByUserIdAndWordKey(USER_ID, WORD_KEY)).thenReturn(Optional.empty());
        ReviewPlan plan = new ReviewPlan();
        plan.setUserId(USER_ID);
        plan.setWordKey(WORD_KEY);
        plan.setStage(1);
        when(reviewPlanRepository.findByUserIdAndWordKey(USER_ID, WORD_KEY)).thenReturn(Optional.of(plan));

        MasterySnapshot after = reviewService.applyQuizResult(USER_ID, WORD_KEY, true, false, ZONE);

        assertThat(after.level()).isEqualTo(MasteryLevel.unlearned);
        assertThat(after.hasQuizHistory()).isTrue();
        assertThat(after.stage()).isEqualTo(2);
        assertThat(after.nextReviewAt()).isEqualTo(LocalDate.now(ZONE).plusDays(4));

        ArgumentCaptor<WordMastery> masteryCaptor = ArgumentCaptor.forClass(WordMastery.class);
        verify(wordMasteryRepository).save(masteryCaptor.capture());
        assertThat(masteryCaptor.getValue().isHasQuizHistory()).isTrue();
    }

    @Test
    void applyQuizResult_wrongOnce_setsFuzzyAndStageDown() {
        WordMastery mastery = new WordMastery();
        mastery.setUserId(USER_ID);
        mastery.setWordKey(WORD_KEY);
        mastery.setLevel(MasteryLevel.unlearned);
        mastery.setHasQuizHistory(true);
        when(wordMasteryRepository.findByUserIdAndWordKey(USER_ID, WORD_KEY)).thenReturn(Optional.of(mastery));

        ReviewPlan plan = new ReviewPlan();
        plan.setUserId(USER_ID);
        plan.setWordKey(WORD_KEY);
        plan.setStage(2);
        when(reviewPlanRepository.findByUserIdAndWordKey(USER_ID, WORD_KEY)).thenReturn(Optional.of(plan));

        MasterySnapshot after = reviewService.applyQuizResult(USER_ID, WORD_KEY, false, false, ZONE);

        assertThat(after.level()).isEqualTo(MasteryLevel.fuzzy);
        assertThat(after.stage()).isEqualTo(1);
        assertThat(after.nextReviewAt()).isEqualTo(LocalDate.now(ZONE).plusDays(1));
    }

    @Test
    void applyQuizResult_consecutiveWrong_setsUnknown() {
        when(wordMasteryRepository.findByUserIdAndWordKey(USER_ID, WORD_KEY)).thenReturn(Optional.empty());
        when(reviewPlanRepository.findByUserIdAndWordKey(USER_ID, WORD_KEY)).thenReturn(Optional.empty());

        MasterySnapshot after = reviewService.applyQuizResult(USER_ID, WORD_KEY, false, true, ZONE);

        assertThat(after.level()).isEqualTo(MasteryLevel.unknown);
        assertThat(after.stage()).isEqualTo(0);
        assertThat(after.nextReviewAt()).isEqualTo(LocalDate.now(ZONE));
        assertThat(after.hasQuizHistory()).isTrue();
    }
}
