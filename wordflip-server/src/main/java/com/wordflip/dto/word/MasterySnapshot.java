package com.wordflip.dto.word;

import com.wordflip.domain.MasteryLevel;
import com.wordflip.domain.WordMastery;

/**
 * 掌握度快照（只读）；无 word_mastery 记录时默认 unlearned + hasQuizHistory=false。
 */
public record MasterySnapshot(MasteryLevel level, boolean hasQuizHistory) {

    public static MasterySnapshot unlearnedDefault() {
        return new MasterySnapshot(MasteryLevel.unlearned, false);
    }

    public static MasterySnapshot from(WordMastery mastery) {
        return new MasterySnapshot(mastery.getLevel(), mastery.isHasQuizHistory());
    }
}
