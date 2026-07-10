package com.wordflip.dto.book;

import com.wordflip.dto.word.WordSummary;

import java.util.List;

/**
 * 导入预览响应（POST /books/import/preview）。
 */
public record BookImportPreviewResponse(
        String previewToken,
        String suggestedName,
        int totalCount,
        Integer deduplicatedCount,
        List<WordSummary> previewWords
) {
}
