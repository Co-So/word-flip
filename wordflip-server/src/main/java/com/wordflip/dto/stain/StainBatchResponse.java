package com.wordflip.dto.stain;

/**
 * 分组一键生成污渍响应，对齐 openapi StainBatchResponse。
 */
public record StainBatchResponse(
        long groupId,
        int updatedCount
) {
}
