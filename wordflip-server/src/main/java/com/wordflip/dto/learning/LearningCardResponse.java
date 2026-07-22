package com.wordflip.dto.learning;

import java.util.List;

/**
 * 词书专属学习卡。
 */
public record LearningCardResponse(
        Long cardId,
        Long lexemeId,
        Long bookId,
        String wordKey,
        String en,
        String phonetic,
        int version,
        List<LearningCardSenseResponse> senses
) {
}
