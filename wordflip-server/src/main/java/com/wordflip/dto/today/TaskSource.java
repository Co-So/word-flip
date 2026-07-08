package com.wordflip.dto.today;

/**
 * 任务来源分组摘要，对齐 openapi TodayTask.sources[]。
 */
public record TaskSource(long groupId, String groupName, int count) {
}
