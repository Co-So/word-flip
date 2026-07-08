package com.wordflip.dto.group;

import com.wordflip.dto.common.PageMeta;
import com.wordflip.dto.word.MasterySnapshot;
import com.wordflip.dto.word.WordSummary;

import java.util.List;

/**
 * 分组单词分页响应（GET /groups/{groupId}/words）。
 */
public record GroupWordsResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<GroupWordItem> words
) {

    public static GroupWordsResponse of(PageMeta meta, List<GroupWordItem> words) {
        return new GroupWordsResponse(meta.page(), meta.size(), meta.totalElements(), meta.totalPages(), words);
    }

    public record GroupWordItem(
            String wordKey,
            String en,
            String cn,
            String pos,
            String ph,
            MasterySnapshot mastery
    ) {
        public static GroupWordItem from(WordSummary summary, MasterySnapshot mastery) {
            return new GroupWordItem(
                    summary.wordKey(),
                    summary.en(),
                    summary.cn(),
                    summary.pos(),
                    summary.ph(),
                    mastery
            );
        }
    }
}
