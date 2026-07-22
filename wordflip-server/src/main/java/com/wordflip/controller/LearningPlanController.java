package com.wordflip.controller;

import com.wordflip.dto.learning.CreateLearningPlanRequest;
import com.wordflip.dto.learning.LearningPlanResponse;
import com.wordflip.dto.learning.PatchLearningPlanRequest;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.LearningPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前主词书与历史学习计划 API。
 */
@RestController
@RequestMapping("/api/v1/learning-plans")
public class LearningPlanController {

    private final LearningPlanService service;

    public LearningPlanController(LearningPlanService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LearningPlanResponse create(@Valid @RequestBody CreateLearningPlanRequest request) {
        return service.createAndActivate(
                SecurityUtils.getCurrentUserId(), request.bookId(), request.resolvedDailyNewCardLimit()
        );
    }

    @GetMapping("/current")
    public LearningPlanResponse current() {
        return service.getCurrent(SecurityUtils.getCurrentUserId());
    }

    @PatchMapping("/current")
    public LearningPlanResponse patch(@Valid @RequestBody PatchLearningPlanRequest request) {
        return service.patchCurrent(
                SecurityUtils.getCurrentUserId(),
                request.planId(),
                request.dailyNewCardLimit(),
                request.status()
        );
    }
}
