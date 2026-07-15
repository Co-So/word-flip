package com.wordflip.dto.word;

import java.util.List;

/**
 * 按词典查询单词详情响应，对齐 openapi WordLookupResponse。
 * <p>
 * 包含指定词典下的完整释义（含全部义项），以及该词典的元信息。
 * 用于详情抽屉内临时切换词典查看释义，不影响用户全局 activeDictId。
 */
public record WordLookupResponse(
        String wordKey,
        String en,
        String cn,
        String pos,
        String ph,
        String enGloss,
        List<SenseDto> senses,
        String dictId,
        String dictName,
        String dictLocale
) {
    public WordLookupResponse {
        if (senses == null) {
            senses = List.of();
        } else {
            senses = List.copyOf(senses);
        }
    }
}
