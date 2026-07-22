package com.wordflip.dto.quiz;

/**
 * 结果页中的错题学习卡快照。
 */
public record QuizWrongCardDto(
        Long cardId,
        Long lexemeId,
        String en,
        String cn,
        String userAnswer
) {
}
