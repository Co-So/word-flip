package com.wordflip.dto.stain;

/**
 * 学习卡污渍响应。
 */
public record WordStainResponse(
        Long cardId,
        Long lexemeId,
        boolean hidden,
        StainConfig config
) {
}
