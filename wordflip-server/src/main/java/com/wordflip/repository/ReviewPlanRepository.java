package com.wordflip.repository;

import com.wordflip.domain.ReviewPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * SRS 复习计划仓储。
 */
public interface ReviewPlanRepository extends JpaRepository<ReviewPlan, Long> {

    List<ReviewPlan> findByUserIdAndWordKeyIn(Long userId, Collection<String> wordKeys);

    Optional<ReviewPlan> findByUserIdAndWordKey(Long userId, String wordKey);
}
