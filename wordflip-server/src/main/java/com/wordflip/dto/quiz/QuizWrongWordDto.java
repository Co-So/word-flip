package com.wordflip.dto.quiz;

/**
 * 错题条目，对齐 openapi QuizResult.wrongWords。
 */
public record QuizWrongWordDto(
        String wordKey,
        String en,
        String cn,
        String userAnswer
) {
}
