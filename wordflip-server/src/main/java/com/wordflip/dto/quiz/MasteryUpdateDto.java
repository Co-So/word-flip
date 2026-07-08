package com.wordflip.dto.quiz;

import com.wordflip.dto.word.MasterySnapshot;

/**
 * 掌握度变更快照，对齐 openapi AnswerResult.masteryUpdate。
 */
public record MasteryUpdateDto(
        String wordKey,
        MasterySnapshot before,
        MasterySnapshot after
) {
}
