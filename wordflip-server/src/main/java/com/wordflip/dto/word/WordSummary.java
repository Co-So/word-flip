package com.wordflip.dto.word;

/**
 * 单词摘要，对齐 openapi WordSummary。
 */
public record WordSummary(
        String wordKey,
        String en,
        String cn,
        String pos,
        String ph
) {
}
