package com.wordflip.dto.quiz;

import java.util.List;

/**
 * 带学习卡与词形标识的不可变题面。
 */
public record QuizQuestionDto(
        int questionIndex,
        Long cardId,
        Long lexemeId,
        String type,
        QuizPromptDto prompt,
        List<QuizOptionDto> options
) {
}
