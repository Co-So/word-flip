package com.wordflip.dto.study;

import com.wordflip.dto.word.MasterySnapshot;
import com.wordflip.dto.word.WordSummary;

/**
 * 学习页单词卡片，对齐 openapi WordCard；image/stain P3 占位为 null。
 */
public record WordCardDto(
        String wordKey,
        String en,
        String cn,
        String pos,
        String ph,
        MasterySnapshot mastery,
        WordDetailDto detail
) {

    public static WordCardDto from(WordSummary summary, MasterySnapshot mastery, WordDetailDto detail) {
        return new WordCardDto(
                summary.wordKey(),
                summary.en(),
                summary.cn(),
                summary.pos(),
                summary.ph(),
                mastery,
                detail
        );
    }
}
