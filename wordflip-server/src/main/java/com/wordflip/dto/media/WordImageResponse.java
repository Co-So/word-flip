package com.wordflip.dto.media;

import java.time.Instant;

/**
 * 卡片图片 API 响应，对齐 openapi WordImageResponse。
 */
public record WordImageResponse(
        String wordKey,
        boolean hasImage,
        String imageUrl,
        String storageKey,
        ImageTransform transform,
        Instant updatedAt
) {
}
