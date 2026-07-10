package com.wordflip.dto.word;

import com.wordflip.domain.HeatDisplayMode;
import com.wordflip.domain.Skill;
import com.wordflip.service.StabilityCalculator;

/**
 * 单词双 skill 进度 + 按用户设置算出的展示热力。
 */
public record WordProgressSnapshot(
        MasterySnapshot dictation,
        MasterySnapshot choice,
        int displayHeatLevel,
        double displayStability,
        HeatDisplayMode heatDisplayMode
) {

    public static WordProgressSnapshot of(
            MasterySnapshot dictation,
            MasterySnapshot choice,
            HeatDisplayMode mode
    ) {
        MasterySnapshot d = dictation != null ? dictation : MasterySnapshot.unlearnedDefault(Skill.dictation);
        MasterySnapshot c = choice != null ? choice : MasterySnapshot.unlearnedDefault(Skill.choice);
        HeatDisplayMode m = mode != null ? mode : HeatDisplayMode.combined;
        int heat;
        double stability;
        switch (m) {
            case dictation -> {
                heat = d.heatLevel();
                stability = d.stability();
            }
            case choice -> {
                heat = c.heatLevel();
                stability = c.stability();
            }
            case free, combined -> {
                // 综合木桶：只聚合「已有测验史」的 skill；未测轨不参与 min，避免单轨测验热力被锁在 0
                boolean dHist = d.hasQuizHistory();
                boolean cHist = c.hasQuizHistory();
                if (dHist && cHist) {
                    heat = Math.min(d.heatLevel(), c.heatLevel());
                    stability = Math.min(d.stability(), c.stability());
                } else if (dHist) {
                    heat = d.heatLevel();
                    stability = d.stability();
                } else if (cHist) {
                    heat = c.heatLevel();
                    stability = c.stability();
                } else {
                    heat = 0;
                    stability = 0.0;
                }
            }
            default -> {
                heat = Math.min(d.heatLevel(), c.heatLevel());
                stability = Math.min(d.stability(), c.stability());
            }
        }
        return new WordProgressSnapshot(d, c, heat, stability, m);
    }

    public static WordProgressSnapshot empty(HeatDisplayMode mode) {
        return of(
                MasterySnapshot.unlearnedDefault(Skill.dictation),
                MasterySnapshot.unlearnedDefault(Skill.choice),
                mode
        );
    }

    /** 用于分组 stats 分档 */
    public int heatBucket() {
        return StabilityCalculator.heatLevel(displayStability);
    }
}
