package com.wordflip.dto.word;

import com.wordflip.domain.MasteryLevel;
import com.wordflip.domain.Skill;
import com.wordflip.domain.WordSkillProgress;
import com.wordflip.service.StabilityCalculator;

import java.time.LocalDate;

/**
 * 单 skill 掌握度快照（热力 + 队列三态 + SRS）。
 */
public record MasterySnapshot(
        MasteryLevel level,
        boolean hasQuizHistory,
        Integer stage,
        LocalDate nextReviewAt,
        double stability,
        int heatLevel,
        Skill skill
) {

    public static MasterySnapshot unlearnedDefault(Skill skill) {
        return new MasterySnapshot(MasteryLevel.unlearned, false, 0, null, 0.0, 0, skill);
    }

    /** 兼容旧调用：默认 dictation */
    public static MasterySnapshot unlearnedDefault() {
        return unlearnedDefault(Skill.dictation);
    }

    public static MasterySnapshot from(WordSkillProgress progress) {
        if (progress == null) {
            return unlearnedDefault();
        }
        double s = StabilityCalculator.fromStored(progress.getStability());
        return new MasterySnapshot(
                progress.getLevel(),
                progress.isHasQuizHistory(),
                progress.getStage(),
                progress.getNextReviewAt(),
                s,
                StabilityCalculator.heatLevel(s),
                progress.getSkill()
        );
    }
}
