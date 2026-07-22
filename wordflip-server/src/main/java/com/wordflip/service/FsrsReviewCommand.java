package com.wordflip.service;

import java.time.Instant;
import java.util.UUID;

/**
 * 一次有效判题触发的双层记忆更新命令。
 */
public record FsrsReviewCommand(
        UUID requestId,
        Long userId,
        Long planId,
        Long cardId,
        Long lexemeId,
        String skill,
        String questionType,
        boolean correct,
        Instant answeredAt
) {
}
