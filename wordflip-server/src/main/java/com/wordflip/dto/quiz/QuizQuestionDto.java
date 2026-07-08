package com.wordflip.dto.quiz;

/**
 * 会话题目快照，对齐 openapi QuizQuestion（不含标准答案）。
 */
public record QuizQuestionDto(
        int questionIndex,
        String wordKey,
        QuizPromptDto prompt
) {
}
