package com.wordflip.service;

import com.wordflip.dto.learning.LearningPlanResponse;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.LearningPlanStore;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理用户唯一当前主词书，同时保留历史学习计划。
 */
@Service
public class LearningPlanService {

    private static final Set<String> STATUSES = Set.of("active", "paused", "completed");
    private final LearningPlanStore store;
    private final GroupService groupService;

    public LearningPlanService(LearningPlanStore store, GroupService groupService) {
        this.store = store;
        this.groupService = groupService;
    }

    @Transactional
    public LearningPlanResponse createAndActivate(Long userId, Long bookId, int dailyNewCardLimit) {
        if (!store.isBookVisible(userId, bookId)) {
            throw new IllegalArgumentException("词书不存在或不可见");
        }
        LearningPlanResponse plan = store.findByUserAndBook(userId, bookId)
                .orElseGet(() -> store.create(userId, bookId, dailyNewCardLimit));
        store.activate(userId, plan.planId());
        // 只为尚未入组的已发布卡片追加分组，切回历史计划不会重建原分组。
        groupService.appendAutoGroups(userId, plan.planId());
        return plan.activated();
    }

    @Transactional(readOnly = true)
    public LearningPlanResponse getCurrent(Long userId) {
        return store.findCurrent(userId)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "尚未选择当前学习计划"));
    }

    @Transactional
    public LearningPlanResponse switchCurrent(Long userId, Long planId) {
        LearningPlanResponse plan = owned(userId, planId);
        store.activate(userId, planId);
        return plan.activated();
    }

    @Transactional
    public LearningPlanResponse patchCurrent(
            Long userId,
            Long planId,
            Integer dailyNewCardLimit,
            String status
    ) {
        LearningPlanResponse target = planId == null ? getCurrent(userId) : owned(userId, planId);
        if (status != null && !STATUSES.contains(status)) {
            throw new IllegalArgumentException("不支持的学习计划状态");
        }
        store.update(userId, target.planId(), dailyNewCardLimit, status);
        if (planId != null) {
            store.activate(userId, target.planId());
        }
        return store.findOwnedPlan(userId, target.planId()).orElseThrow().activated();
    }

    private LearningPlanResponse owned(Long userId, Long planId) {
        return store.findOwnedPlan(userId, planId)
                .orElseThrow(() -> new IllegalArgumentException("学习计划不存在或不属于当前用户"));
    }
}
