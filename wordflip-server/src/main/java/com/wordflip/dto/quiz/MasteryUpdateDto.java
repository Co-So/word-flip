package com.wordflip.dto.quiz;

import com.wordflip.dto.learning.FsrsMemoryResponse;

/**
 * 一次判题前后的权威卡片 FSRS 状态。
 */
public record MasteryUpdateDto(
        Long cardId,
        Long lexemeId,
        FsrsMemoryResponse before,
        FsrsMemoryResponse after
) {
}
