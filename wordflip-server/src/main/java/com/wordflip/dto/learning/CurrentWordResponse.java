package com.wordflip.dto.learning;

import java.util.List;

/**
 * 按 wordKey 查询当前词书学习卡与来源资料。
 */
public record CurrentWordResponse(
        Long lexemeId,
        String wordKey,
        String en,
        LearningCardResponse currentCard,
        List<SourceMaterialResponse> sourceMaterials
) {
}
