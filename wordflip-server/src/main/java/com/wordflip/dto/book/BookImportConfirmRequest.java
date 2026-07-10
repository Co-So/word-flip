package com.wordflip.dto.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 确认导入请求（POST /books/import）。
 */
public record BookImportConfirmRequest(
        @NotBlank String previewToken,
        @NotBlank @Size(min = 1, max = 64) String name
) {
}
