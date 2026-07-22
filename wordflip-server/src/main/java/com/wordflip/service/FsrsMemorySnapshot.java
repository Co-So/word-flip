package com.wordflip.service;

import java.time.Instant;

/**
 * 与数据库无关的 FSRS 状态快照，便于审计旧状态与新状态。
 */
public record FsrsMemorySnapshot(
        String state,
        Integer step,
        double stability,
        double difficulty,
        Instant dueAt,
        Instant lastReviewAt,
        int reps,
        int lapses,
        int elapsedDays,
        int scheduledDays
) {
    /**
     * 创建尚未作答的新卡片状态。
     */
    public static FsrsMemorySnapshot newCard(Instant now) {
        return new FsrsMemorySnapshot("new", null, 0, 0, now, null, 0, 0, 0, 0);
    }
}
