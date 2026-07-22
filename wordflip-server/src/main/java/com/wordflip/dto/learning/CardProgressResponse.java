package com.wordflip.dto.learning;

/**
 * 学习卡默写与选择双轨进度。
 */
public record CardProgressResponse(
        FsrsMemoryResponse dictation,
        FsrsMemoryResponse choice,
        int displayHeatLevel
) {
}
