package com.wordflip.dto.book;

import java.util.List;

/**
 * 当前用户可见的词书列表。
 */
public record BookListResponse(List<BookItem> books) {

    /**
     * selected 表示该书对应计划当前激活，不再表示多选勾选。
     */
    public record BookItem(
            long id,
            String name,
            String source,
            int wordCount,
            Integer declaredCount,
            boolean selected,
            boolean canDelete
    ) {
    }
}
