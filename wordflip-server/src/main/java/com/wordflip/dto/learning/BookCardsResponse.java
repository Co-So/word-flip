package com.wordflip.dto.learning;

import java.util.List;

/**
 * 词书已发布学习卡分页响应。
 */
public record BookCardsResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<LearningCardResponse> cards
) {
}
