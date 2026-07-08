package com.wordflip.dto.quiz;

/**
 * 单题题干，对齐 openapi QuizQuestion.prompt。
 */
public record QuizPromptDto(
        String cn,
        String pos,
        String ph
) {
}
