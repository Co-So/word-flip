package com.wordflip.dto.word;

/**
 * 例句 DTO，对齐 openapi Example。
 */
public record ExampleDto(
        String en,
        String cn,
        int sortOrder
) {
}
