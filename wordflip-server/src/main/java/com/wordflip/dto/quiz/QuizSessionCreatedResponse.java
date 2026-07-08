package com.wordflip.dto.quiz;

/**
 * POST /quiz/sessions 201 响应，对齐 openapi QuizSessionCreated。
 */
public record QuizSessionCreatedResponse(
        String sessionId,
        String status,
        int totalQuestions,
        int currentIndex,
        int score,
        QuizQuestionDto question
) {
}
