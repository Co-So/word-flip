package com.wordflip.dto.group;

import com.wordflip.dto.learning.LearningCardDetailResponse;
import java.util.List;

/**
 * 当前计划尚未加入任何分组的已发布学习卡。
 */
public record UnassignedCardsResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<LearningCardDetailResponse> cards
) {
}
