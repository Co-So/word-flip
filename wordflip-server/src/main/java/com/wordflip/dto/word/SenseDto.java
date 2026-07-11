package com.wordflip.dto.word;

import java.util.List;

/**
 * 义项 DTO，对齐 openapi Sense。
 * <p>
 * 英汉：{@code cn} 有值；英英：{@code enGloss} 有值、{@code cn} 可空。
 */
public record SenseDto(
        Long id,
        String pos,
        String cn,
        String enGloss,
        boolean primary,
        String quality,
        int sortOrder,
        List<ExampleDto> examples
) {
    public SenseDto {
        if (examples == null) {
            examples = List.of();
        } else {
            examples = List.copyOf(examples);
        }
        if (quality == null || quality.isBlank()) {
            quality = "ok";
        }
    }

    /** 兼容旧调用（无 enGloss） */
    public SenseDto(
            Long id,
            String pos,
            String cn,
            boolean primary,
            String quality,
            int sortOrder,
            List<ExampleDto> examples
    ) {
        this(id, pos, cn, null, primary, quality, sortOrder, examples);
    }
}
