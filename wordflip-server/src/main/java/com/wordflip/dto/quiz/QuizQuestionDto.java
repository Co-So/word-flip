package com.wordflip.dto.quiz;

import java.util.List;

/**
 * 会话题目快照，对齐 openapi QuizQuestion（不含标准答案）。
 */
public record QuizQuestionDto(
        int questionIndex,
        String wordKey,
        String type,
        QuizPromptDto prompt,
        List<QuizOptionDto> options
) {
    public QuizQuestionDto(int questionIndex, String wordKey, QuizPromptDto prompt) {
        this(questionIndex, wordKey, "dictation", prompt, null);
    }
}
