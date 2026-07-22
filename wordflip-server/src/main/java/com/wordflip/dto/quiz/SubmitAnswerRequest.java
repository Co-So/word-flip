package com.wordflip.dto.quiz;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 测验答案提交；requestId 由客户端生成，重试时必须复用。
 */
public record SubmitAnswerRequest(
        @NotNull UUID requestId,
        @NotNull Integer questionIndex,
        String answer,
        String selectedKey
) {
}
