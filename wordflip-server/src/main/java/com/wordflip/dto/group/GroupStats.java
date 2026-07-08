package com.wordflip.dto.group;

/**
 * 分组四维统计，对齐 openapi GroupStats。
 */
public record GroupStats(int unlearned, int fuzzy, int unknown, int total) {
}
