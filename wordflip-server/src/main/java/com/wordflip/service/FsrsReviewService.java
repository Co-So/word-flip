package com.wordflip.service;

import com.wordflip.repository.FsrsReviewStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 有效判题更新双层记忆的唯一事务入口。
 */
@Service
public class FsrsReviewService {

    private final FsrsReviewStore store;
    private final FsrsSchedulerService scheduler;

    public FsrsReviewService(FsrsReviewStore store, FsrsSchedulerService scheduler) {
        this.store = store;
        this.scheduler = scheduler;
    }

    /**
     * 同一事务写审计事件、卡片 FSRS 与跨书词形熟悉度；重复 requestId 不重复调度。
     */
    @Transactional
    public FsrsScheduleResult applyQuizResult(FsrsReviewCommand command) {
        var existing = store.findByRequestId(command.requestId());
        if (existing.isPresent()) {
            return existing.get();
        }
        FsrsMemorySnapshot before = store.lockCardMemory(
                command.userId(), command.cardId(), command.skill(), command.answeredAt()
        );
        store.lockLexemeMemory(command.userId(), command.lexemeId(), command.skill());
        FsrsScheduleResult result = scheduler.schedule(
                command.cardId(), before, command.correct(), command.answeredAt()
        );
        // 事件先记录旧/新状态，再更新两个物化记忆；事务失败时三者一起回滚。
        store.insertReviewEvent(command, result);
        store.updateCardMemory(command.userId(), command.cardId(), command.skill(), result.after());
        store.updateLexemeMemory(
                command.userId(), command.lexemeId(), command.skill(), command.correct(), command.answeredAt()
        );
        return result;
    }
}
