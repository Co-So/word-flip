package com.wordflip.service;

import com.wordflip.domain.StudyGroup;
import com.wordflip.dto.today.TodayDashboard;
import com.wordflip.repository.GroupRepository;
import com.wordflip.repository.GroupWordRepository;
import com.wordflip.repository.TodayQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Today 计数冒烟：新用户 newWords = 已入组词数（P1-T01 后端侧）。
 */
@ExtendWith(MockitoExtension.class)
class TodayServiceTest {

    @Mock
    private TodayQueryRepository todayQueryRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupWordRepository groupWordRepository;
    @Mock
    private ReviewService reviewService;
    @Mock
    private TodayCacheService todayCacheService;

    @InjectMocks
    private TodayService todayService;

    @Test
    void buildDashboard_newUser_newWordsEqualsAssignedTotal() {
        LocalDate today = LocalDate.of(2026, 7, 8);
        when(todayQueryRepository.countAssignedWords(1L)).thenReturn(42L);
        when(todayQueryRepository.countMastered(1L, today)).thenReturn(0L);
        when(todayQueryRepository.countDueReview(1L, today)).thenReturn(0L);
        when(todayQueryRepository.countNewWords(1L)).thenReturn(42L);
        when(todayQueryRepository.countQuizPool(1L, today)).thenReturn(0L);
        when(groupRepository.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(sampleGroup()));
        when(todayQueryRepository.countNewWordsInGroup(1L, 10L)).thenReturn(42L);
        when(todayQueryRepository.countDueReviewInGroup(eq(1L), eq(10L), eq(today))).thenReturn(0L);
        when(reviewService.calculateStreakDays(1L, today)).thenReturn(0);

        TodayDashboard dashboard = todayService.buildDashboard(1L, today);

        assertThat(dashboard.tasks().newWords().count()).isEqualTo(42);
        assertThat(dashboard.stats().completionPercent()).isZero();
        assertThat(dashboard.recommendedStudy()).isNotNull();
    }

    private static StudyGroup sampleGroup() {
        StudyGroup group = new StudyGroup();
        group.setId(10L);
        group.setUserId(1L);
        group.setName("第1组");
        return group;
    }
}
