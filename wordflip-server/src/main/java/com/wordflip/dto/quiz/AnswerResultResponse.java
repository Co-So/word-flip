package com.wordflip.dto.quiz;

/**
 * POST /quiz/sessions/{sessionId}/answer 200 响应，对齐 openapi AnswerResult。
 *
 * @param expectedEn     答错时英文标准答案（巩固默写用）
 * @param expectedAnswer 答错时按题型展示的正确答案文案（英选中=中文释义，其余=英文）
 */
public record AnswerResultResponse(
        boolean correct,
        String expectedEn,
        String expectedAnswer,
        String feedback,
        MasteryUpdateDto masteryUpdate,
        QuizSessionProgressDto session
) {
}
