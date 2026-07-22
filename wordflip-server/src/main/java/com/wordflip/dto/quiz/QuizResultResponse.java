package com.wordflip.dto.quiz;

import java.util.List;

/**
 * GET /quiz/sessions/{sessionId}/result 响应，对齐 openapi QuizResult。
 */
public record QuizResultResponse(
        String sessionId,
        int score,
        int total,
        int correctCount,
        int wrongCount,
        float accuracy,
        String rating,
        List<QuizWrongCardDto> wrongCards
) {
}
