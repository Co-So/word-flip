package com.wordflip.controller;

import com.wordflip.dto.stats.AchievementsResponse;
import com.wordflip.dto.stats.StatsHeatmapResponse;
import com.wordflip.dto.stats.StatsSummaryResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.StatsService;
import com.wordflip.util.UserTimeZoneUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前学习计划的统计与成就 API。
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public StatsSummaryResponse summary(
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        return service.summary(SecurityUtils.getCurrentUserId(), UserTimeZoneUtil.resolveZone(timezone));
    }

    @GetMapping("/heatmap")
    public StatsHeatmapResponse heatmap(
            @RequestParam(defaultValue = "3") int months,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        return service.heatmap(
                SecurityUtils.getCurrentUserId(), months, UserTimeZoneUtil.resolveZone(timezone)
        );
    }

    @GetMapping("/achievements")
    public AchievementsResponse achievements() {
        return service.achievements(SecurityUtils.getCurrentUserId());
    }
}
