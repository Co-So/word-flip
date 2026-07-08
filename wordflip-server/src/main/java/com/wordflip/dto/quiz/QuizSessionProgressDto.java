package com.wordflip.dto.quiz;

/**
 * 答题后会话进度，对齐 openapi AnswerResult.session。
 */
public record QuizSessionProgressDto(
        String status,
        int score,
        int currentIndex,
        int totalQuestions,
        QuizQuestionDto nextQuestion
) {
}
