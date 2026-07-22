package com.wordflip.dto.learning;

import java.util.List;

/**
 * 学习卡考义、双轨进度与来源资料详情。
 */
public record LearningCardDetailResponse(
        Long cardId,
        Long lexemeId,
        Long bookId,
        String wordKey,
        String en,
        String phonetic,
        int version,
        List<LearningCardSenseResponse> senses,
        CardProgressResponse progress,
        List<SourceMaterialResponse> sourceMaterials
) {

    /**
     * 将内部学习卡对象展开为 OpenAPI 约定的根节点字段。
     */
    public static LearningCardDetailResponse from(
            LearningCardResponse card,
            CardProgressResponse progress,
            List<SourceMaterialResponse> sourceMaterials
    ) {
        return new LearningCardDetailResponse(
                card.cardId(), card.lexemeId(), card.bookId(), card.wordKey(), card.en(),
                card.phonetic(), card.version(), card.senses(), progress, sourceMaterials
        );
    }
}
