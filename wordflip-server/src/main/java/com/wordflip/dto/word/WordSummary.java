package com.wordflip.dto.word;

import java.util.List;

/**
 * 单词摘要，对齐 openapi WordSummary。
 * <p>
 * 顶层 cn/pos/ph/enGloss = 展示义（primary 或 exam）；senses 为完整义项。
 */
public record WordSummary(
        String wordKey,
        String en,
        String cn,
        String pos,
        String ph,
        String enGloss,
        List<SenseDto> senses
) {
    public WordSummary {
        if (senses == null) {
            senses = List.of();
        } else {
            senses = List.copyOf(senses);
        }
    }

    /** 兼容旧调用：无 enGloss / senses */
    public WordSummary(String wordKey, String en, String cn, String pos, String ph) {
        this(wordKey, en, cn, pos, ph, null, List.of());
    }

    /** 兼容旧调用：无 enGloss */
    public WordSummary(String wordKey, String en, String cn, String pos, String ph, List<SenseDto> senses) {
        this(wordKey, en, cn, pos, ph, null, senses);
    }

    public WordSummary withCn(String newCn) {
        return new WordSummary(wordKey, en, newCn, pos, ph, enGloss, senses);
    }

    public WordSummary withSenses(List<SenseDto> newSenses) {
        return new WordSummary(wordKey, en, cn, pos, ph, enGloss, newSenses);
    }
}
