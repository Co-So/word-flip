package com.wordflip.dto.quiz;

/**
 * POST /quiz/sessions/{sessionId}/answer 200 响应，对齐 openapi AnswerResult。
 */
public record AnswerResultResponse(
        boolean correct,
        String expectedEn,
        String feedback,
        MasteryUpdateDto masteryUpdate,
        QuizSessionProgressDto session
) {
}
