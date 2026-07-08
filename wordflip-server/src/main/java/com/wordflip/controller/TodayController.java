package com.wordflip.controller;

import com.wordflip.dto.today.TodayDashboard;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.TodayService;
import com.wordflip.util.UserTimeZoneUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;

/**
 * 今日仪表盘 API：GET /today（P1-B03~B07）。
 */
@RestController
@RequestMapping("/api/v1")
public class TodayController {

    private final TodayService todayService;

    public TodayController(TodayService todayService) {
        this.todayService = todayService;
    }

    @GetMapping("/today")
    public TodayDashboard getToday(
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        ZoneId zoneId = UserTimeZoneUtil.resolveZone(timezone);
        return todayService.getDashboard(SecurityUtils.getCurrentUserId(), zoneId);
    }
}
