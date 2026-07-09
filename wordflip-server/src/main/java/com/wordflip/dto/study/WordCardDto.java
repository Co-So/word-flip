package com.wordflip.dto.study;

import com.wordflip.dto.media.WordImagePayload;
import com.wordflip.dto.stain.WordStainPayload;
import com.wordflip.dto.word.MasterySnapshot;
import com.wordflip.dto.word.WordSummary;

/**
 * 学习页单词卡片，对齐 openapi WordCard（含 image / stain）。
 */
public record WordCardDto(
        String wordKey,
        String en,
        String cn,
        String pos,
        String ph,
        MasterySnapshot mastery,
        WordDetailDto detail,
        WordImagePayload image,
        WordStainPayload stain
) {

    public static WordCardDto from(
            WordSummary summary,
            MasterySnapshot mastery,
            WordDetailDto detail,
            WordImagePayload image,
            WordStainPayload stain
    ) {
        return new WordCardDto(
                summary.wordKey(),
                summary.en(),
                summary.cn(),
                summary.pos(),
                summary.ph(),
                mastery,
                detail,
                image != null ? image : WordImagePayload.none(),
                stain
        );
    }
}
