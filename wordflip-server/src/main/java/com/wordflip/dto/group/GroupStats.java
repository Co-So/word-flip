package com.wordflip.dto.group;

/**
 * 分组热力分档统计，对齐 openapi GroupStats（REQ-GROUP-2）。
 */
public record GroupStats(
        int heat0,
        int heat1,
        int heat2,
        int heat3,
        int heat4,
        int total
) {
}
