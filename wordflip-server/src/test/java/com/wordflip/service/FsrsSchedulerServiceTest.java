package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * FSRS 评分映射与配置测试。
 */
class FsrsSchedulerServiceTest {

    private final FsrsSchedulerService service = new FsrsSchedulerService(0.90, 36500);

    @Test
    void correctMapsToGoodAndProducesReviewState() {
        var now = Instant.parse("2026-07-16T00:00:00Z");

        var result = service.schedule(42L, FsrsMemorySnapshot.newCard(now), true, now);

        assertThat(result.rating()).isEqualTo("good");
        assertThat(result.after().state()).isIn("learning", "review");
        assertThat(result.after().dueAt()).isAfter(now);
        assertThat(result.after().reps()).isEqualTo(1);
        assertThat(result.after().lapses()).isZero();
    }

    @Test
    void wrongMapsToAgainAndIncrementsLapses() {
        var now = Instant.parse("2026-07-16T00:00:00Z");

        var result = service.schedule(42L, FsrsMemorySnapshot.newCard(now), false, now);

        assertThat(result.rating()).isEqualTo("again");
        assertThat(result.after().reps()).isEqualTo(1);
        assertThat(result.after().lapses()).isEqualTo(1);
    }

    @Test
    void schedulerUsesApprovedRetentionAndMaximumInterval() {
        assertThat(service.desiredRetention()).isEqualTo(0.90);
        assertThat(service.maximumIntervalDays()).isEqualTo(36500);
    }
}
