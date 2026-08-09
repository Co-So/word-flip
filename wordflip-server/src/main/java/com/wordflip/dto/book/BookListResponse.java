package com.wordflip.dto.book;

import java.util.List;

/**
 * 当前用户可见的词书列表。
 */
public record BookListResponse(List<BookItem> books) {

    /**
     * 已分配到学习计划中的卡片进度。
     */
    public record BookProgress(int masteredCount, int assignedCardCount, int completionPercent) {
    }

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
            boolean canDelete,
            Long planId,
            String planStatus,
            BookProgress progress
    ) {

        /**
         * 兼容新导入词书尚未创建学习计划的响应构造。
         */
        public BookItem(
                long id,
                String name,
                String source,
                int wordCount,
                Integer declaredCount,
                boolean selected,
                boolean canDelete
        ) {
            this(id, name, source, wordCount, declaredCount, selected, canDelete, null, null, null);
        }
    }
}
