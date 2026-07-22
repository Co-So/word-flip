package com.wordflip.dto.learning;

import java.time.Instant;

/**
 * 客户端只读的 FSRS 状态。
 */
public record FsrsMemoryResponse(
        String state,
        Instant dueAt,
        double stability,
        double difficulty,
        int reps,
        int lapses
) {
}
