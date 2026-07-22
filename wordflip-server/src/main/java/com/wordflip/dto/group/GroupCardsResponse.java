package com.wordflip.dto.group;

import com.wordflip.dto.learning.LearningCardDetailResponse;
import java.util.List;

/**
 * 分组学习卡分页响应。
 */
public record GroupCardsResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<LearningCardDetailResponse> cards
) {
}
