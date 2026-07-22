package com.wordflip.repository;

import com.wordflip.service.FsrsMemorySnapshot;
import com.wordflip.service.FsrsReviewCommand;
import com.wordflip.service.FsrsScheduleResult;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * FSRS 双层记忆和不可变审计日志持久化端口。
 */
public interface FsrsReviewStore {
    Optional<FsrsScheduleResult> findByRequestId(UUID requestId);

    FsrsMemorySnapshot lockCardMemory(Long userId, Long cardId, String skill, Instant now);

    void lockLexemeMemory(Long userId, Long lexemeId, String skill);

    void insertReviewEvent(FsrsReviewCommand command, FsrsScheduleResult result);

    void updateCardMemory(Long userId, Long cardId, String skill, FsrsMemorySnapshot after);

    void updateLexemeMemory(Long userId, Long lexemeId, String skill, boolean correct, Instant answeredAt);
}
