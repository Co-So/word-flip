package com.wordflip.dto.stain;

/**
 * 学习卡片内嵌污渍摘要，对齐 openapi WordStainPayload。
 */
public record WordStainPayload(
        boolean hidden,
        StainConfig config
) {
}
