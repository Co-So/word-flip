package com.wordflip.dto.quiz;

/**
 * 单题题干，对齐 openapi QuizQuestion.prompt。
 * choice_en_cn 可带 en；dictation / choice_cn_en 主要用 cn。
 */
public record QuizPromptDto(
        String cn,
        String pos,
        String ph,
        String en
) {
    public QuizPromptDto(String cn, String pos, String ph) {
        this(cn, pos, ph, null);
    }
}
