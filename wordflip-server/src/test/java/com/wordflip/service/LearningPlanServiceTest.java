package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wordflip.dto.learning.LearningPlanResponse;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.LearningPlanStore;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 唯一当前学习计划业务测试。
 */
@ExtendWith(MockitoExtension.class)
class LearningPlanServiceTest {

    @Mock
    private LearningPlanStore store;
    @Mock
    private GroupService groupService;

    @Test
    void createPlanActivatesItAfterOwnershipValidation() {
        var service = new LearningPlanService(store, groupService);
        var plan = plan(9L, 2L, true);
        when(store.isBookVisible(1L, 2L)).thenReturn(true);
        when(store.findByUserAndBook(1L, 2L)).thenReturn(Optional.empty());
        when(store.create(1L, 2L, 20)).thenReturn(plan);

        var result = service.createAndActivate(1L, 2L, 20);

        assertThat(result.planId()).isEqualTo(9L);
        var order = inOrder(store);
        order.verify(store).isBookVisible(1L, 2L);
        order.verify(store).findByUserAndBook(1L, 2L);
        order.verify(store).create(1L, 2L, 20);
        order.verify(store).activate(1L, 9L);
        verify(groupService).appendAutoGroups(1L, 9L);
    }

    @Test
    void switchingPlanRejectsAnotherUsersPlan() {
        var service = new LearningPlanService(store, groupService);
        when(store.findOwnedPlan(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.switchCurrent(1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("学习计划不存在");
        verify(store).findOwnedPlan(1L, 99L);
    }

    @Test
    void currentPlanReturnsNotFoundWhenUserHasNotSelectedBook() {
        var service = new LearningPlanService(store, groupService);
        when(store.findCurrent(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(1L))
                .isInstanceOf(WordflipException.class)
                .extracting(error -> ((WordflipException) error).getCode())
                .isEqualTo("NOT_FOUND");
        verify(store).findCurrent(1L);
    }

    private static LearningPlanResponse plan(Long planId, Long bookId, boolean active) {
        return new LearningPlanResponse(
                planId, bookId, "四级高频词汇", "active", 20, active,
                Instant.parse("2026-07-16T00:00:00Z")
        );
    }
}
