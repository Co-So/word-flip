package com.wordflip.dto.word;

import com.wordflip.dto.common.PageMeta;

import java.util.List;

/**
 * 未入组词池响应（GET /words/unassigned）。
 */
public record UnassignedWordsResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<WordSummary> words
) {

    public static UnassignedWordsResponse of(PageMeta meta, List<WordSummary> words) {
        return new UnassignedWordsResponse(meta.page(), meta.size(), meta.totalElements(), meta.totalPages(), words);
    }
}
