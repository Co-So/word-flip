package com.wordflip.service;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 对锁定版本 Java FSRS 的窄封装；客户端只能提交答案，不能提交评分。
 */
@Service
public class FsrsSchedulerService {

    private final Scheduler scheduler;

    public FsrsSchedulerService(
            @Value("${wordflip.fsrs.desired-retention:0.90}") double desiredRetention,
            @Value("${wordflip.fsrs.maximum-interval-days:36500}") int maximumIntervalDays
    ) {
        this.scheduler = Scheduler.builder()
                .desiredRetention(desiredRetention)
                .maximumInterval(maximumIntervalDays)
                // 服务端测试和重复请求必须得到确定性结果。
                .enableFuzzing(false)
                .build();
    }

    /**
     * 将答错映射 Again、答对映射 Good，并返回可写入审计日志的完整状态。
     */
    public FsrsScheduleResult schedule(
            Long cardId,
            FsrsMemorySnapshot before,
            boolean correct,
            Instant reviewedAt
    ) {
        Rating rating = correct ? Rating.GOOD : Rating.AGAIN;
        Card card = toCard(cardId, before);
        Card updated = scheduler.reviewCard(card, rating, reviewedAt).card();

        int elapsedDays = before.lastReviewAt() == null
                ? 0
                : Math.max(0, (int) ChronoUnit.DAYS.between(before.lastReviewAt(), reviewedAt));
        int scheduledDays = Math.max(0, (int) Duration.between(reviewedAt, updated.getDue()).toDays());
        FsrsMemorySnapshot after = new FsrsMemorySnapshot(
                updated.getState().name().toLowerCase(),
                updated.getStep(),
                valueOrZero(updated.getStability()),
                valueOrZero(updated.getDifficulty()),
                updated.getDue(),
                updated.getLastReview(),
                before.reps() + 1,
                before.lapses() + (correct ? 0 : 1),
                elapsedDays,
                scheduledDays
        );
        return new FsrsScheduleResult(rating.name().toLowerCase(), before, after);
    }

    public double desiredRetention() {
        return scheduler.getDesiredRetention();
    }

    public int maximumIntervalDays() {
        return scheduler.getMaximumInterval();
    }

    private Card toCard(Long cardId, FsrsMemorySnapshot memory) {
        var builder = Card.builder()
                // 库内部 cardId 为 int，仅用于生成 ReviewLog；数据库仍以 Long cardId 为准。
                .cardId(cardId.hashCode())
                .due(memory.dueAt())
                .lastReview(memory.lastReviewAt());
        if (!"new".equals(memory.state())) {
            builder.state(State.valueOf(memory.state().toUpperCase()))
                    .step(memory.step())
                    .stability(memory.stability())
                    .difficulty(memory.difficulty());
        }
        return builder.build();
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0 : value;
    }
}
