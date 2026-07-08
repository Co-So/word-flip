package com.wordflip.dto.word;

import com.wordflip.domain.MasteryLevel;
import com.wordflip.domain.ReviewPlan;
import com.wordflip.domain.WordMastery;

import java.time.LocalDate;

/**
 * 掌握度快照（只读）；无 word_mastery 记录时默认 unlearned + hasQuizHistory=false。
 */
public record MasterySnapshot(
        MasteryLevel level,
        boolean hasQuizHistory,
        Integer stage,
        LocalDate nextReviewAt
) {

    public static MasterySnapshot unlearnedDefault() {
        return new MasterySnapshot(MasteryLevel.unlearned, false, 0, null);
    }

    /** Groups 读 API 兼容：无 review_plans 时不填 stage/nextReviewAt */
    public static MasterySnapshot from(WordMastery mastery) {
        return new MasterySnapshot(mastery.getLevel(), mastery.isHasQuizHistory(), null, null);
    }

    /** Study/Today 完整快照：合并 review_plans（REQ-EBBING） */
    public static MasterySnapshot from(WordMastery mastery, ReviewPlan plan) {
        Integer stageValue = plan != null ? plan.getStage() : 0;
        LocalDate nextReview = plan != null ? plan.getNextReviewAt() : null;
        return new MasterySnapshot(
                mastery.getLevel(),
                mastery.isHasQuizHistory(),
                stageValue,
                nextReview
        );
    }

    public static MasterySnapshot withPlan(ReviewPlan plan) {
        if (plan == null) {
            return unlearnedDefault();
        }
        return new MasterySnapshot(MasteryLevel.unlearned, false, plan.getStage(), plan.getNextReviewAt());
    }
}
