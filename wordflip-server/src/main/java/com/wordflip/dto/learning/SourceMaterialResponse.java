package com.wordflip.dto.learning;

import java.util.List;

/**
 * 详情抽屉中的可追溯词典来源资料。
 */
public record SourceMaterialResponse(
        String sourceId,
        String sourceName,
        String revision,
        String licenseNote,
        Long rawEntryId,
        List<LearningCardSenseResponse> senses
) {
}
