package com.wordflip.dto.stats;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习日志热力图。
 */
public record StatsHeatmapResponse(LocalDate startDate, LocalDate endDate, List<Day> days) {

    public record Day(LocalDate date, int level) {
    }
}
