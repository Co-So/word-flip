package com.wordflip.dto.today;

import java.util.List;

/**
 * 今日单条任务，对齐 openapi TodayTask。
 */
public record TodayTask(int count, String label, List<TaskSource> sources) {

    public static TodayTask of(int count, String label) {
        return new TodayTask(count, label, List.of());
    }

    public static TodayTask of(int count, String label, List<TaskSource> sources) {
        return new TodayTask(count, label, sources == null ? List.of() : sources);
    }
}
