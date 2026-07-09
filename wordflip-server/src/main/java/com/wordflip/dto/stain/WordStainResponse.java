package com.wordflip.dto.stain;

/**
 * GET/PUT /words/{wordKey}/stain 响应，对齐 openapi WordStainResponse。
 */
public record WordStainResponse(
        String wordKey,
        boolean hidden,
        StainConfig config
) {
}
