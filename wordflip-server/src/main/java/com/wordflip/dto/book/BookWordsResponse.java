package com.wordflip.dto.book;

import com.wordflip.dto.common.PageMeta;
import com.wordflip.dto.word.WordSummary;

import java.util.List;

/**
 * 词书词条分页响应（GET /books/{bookId}/words）。
 */
public record BookWordsResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<WordSummary> words
) {

    public static BookWordsResponse of(PageMeta meta, List<WordSummary> words) {
        return new BookWordsResponse(
                meta.page(),
                meta.size(),
                meta.totalElements(),
                meta.totalPages(),
                words
        );
    }
}
