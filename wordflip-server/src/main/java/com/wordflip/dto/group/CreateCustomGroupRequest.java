package com.wordflip.dto.group;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 使用当前计划未入组卡片创建手动分组。
 */
public record CreateCustomGroupRequest(
        @NotEmpty @Size(min = 1, max = 500) List<Long> cardIds,
        @Size(max = 64) String name
) {
}
