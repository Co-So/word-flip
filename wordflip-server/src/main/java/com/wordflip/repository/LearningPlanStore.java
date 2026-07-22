package com.wordflip.repository;

import com.wordflip.dto.learning.LearningPlanResponse;
import java.util.Optional;

/**
 * 学习计划持久化端口，隐藏行锁与当前计划指针细节。
 */
public interface LearningPlanStore {
    boolean isBookVisible(Long userId, Long bookId);

    Optional<LearningPlanResponse> findByUserAndBook(Long userId, Long bookId);

    Optional<LearningPlanResponse> findOwnedPlan(Long userId, Long planId);

    Optional<LearningPlanResponse> findCurrent(Long userId);

    LearningPlanResponse create(Long userId, Long bookId, int dailyNewCardLimit);

    void activate(Long userId, Long planId);

    void update(Long userId, Long planId, Integer dailyNewCardLimit, String status);
}
