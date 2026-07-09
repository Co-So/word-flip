package com.wordflip.dto.group;

import com.wordflip.dto.common.PageMeta;
import com.wordflip.dto.word.MasterySnapshot;
import com.wordflip.dto.word.WordProgressSnapshot;
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

    /**
     * 组内单词项：mastery 保留 dictation 兼容；progress 为双 skill + 展示热力。
     */
    public record GroupWordItem(
            String wordKey,
            String en,
            String cn,
            String pos,
            String ph,
            MasterySnapshot mastery,
            WordProgressSnapshot progress
    ) {
        public static GroupWordItem from(WordSummary summary, WordProgressSnapshot progress) {
            MasterySnapshot mastery = progress != null
                    ? progress.dictation()
                    : MasterySnapshot.unlearnedDefault();
            return new GroupWordItem(
                    summary.wordKey(),
                    summary.en(),
                    summary.cn(),
                    summary.pos(),
                    summary.ph(),
                    mastery,
                    progress
            );
        }
    }
}
