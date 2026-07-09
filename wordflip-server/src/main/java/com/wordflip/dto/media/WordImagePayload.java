package com.wordflip.dto.media;

/**
 * 学习卡片内嵌图片摘要，对齐 openapi WordImagePayload。
 */
public record WordImagePayload(
        boolean hasImage,
        String imageUrl,
        ImageTransform transform
) {
    /** 无图占位 */
    public static WordImagePayload none() {
        return new WordImagePayload(false, null, null);
    }
}
