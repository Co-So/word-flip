package com.wordflip.dto.learning;

import java.util.List;

/**
 * 词书学习卡专属义项。
 */
public record LearningCardSenseResponse(
        Long id,
        String pos,
        String cn,
        String enGloss,
        boolean primary,
        String quality,
        int sortOrder,
        List<LearningCardExampleResponse> examples
) {
}
