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
                // 综合：取较低 heat（木桶）；free 默认也先给综合，客户端可再切换
                heat = Math.min(d.heatLevel(), c.heatLevel());
                stability = Math.min(d.stability(), c.stability());
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
